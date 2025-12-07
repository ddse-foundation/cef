package org.ddse.ml.cef.service;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.ddse.ml.cef.retriever.VectorRetrievalRequest;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of KnowledgeRetriever.
 * 
 * Implements 3-level fallback strategy:
 * 1. Graph-only (when graphHints provided)
 * 2. Hybrid (semantic search + graph expansion)
 * 3. Vector-only (fallback if graph empty)
 *
 * @author mrmanna
 */
@Service
@Primary
public class KnowledgeRetrieverImpl implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrieverImpl.class);

    private final GraphStore graphStore;
    private final ChunkStore chunkStore;
    private final EmbeddingModel embeddingModel;
    private final CefProperties properties;
    private final PatternExecutor patternExecutor;

    public KnowledgeRetrieverImpl(GraphStore graphStore,
            ChunkStore chunkStore,
            EmbeddingModel embeddingModel,
            CefProperties properties,
            PatternExecutor patternExecutor) {
        this.graphStore = graphStore;
        this.chunkStore = chunkStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.patternExecutor = patternExecutor;
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        long startTime = System.currentTimeMillis();

        log.debug("Retrieving context for request: {}", request);

        List<String> semanticKeywords = request.semanticKeywords() != null ? request.semanticKeywords() : List.of();

        // Pattern-based traversal if patterns are provided
        if (request.graphQuery() != null && request.graphQuery().usePatternsForTraversal()) {
            log.info("Using PATTERN_BASED_TRAVERSAL strategy");
            return executePatternBasedRetrieval(request, startTime);
        }

        if (hasGraphTargets(request)) {
            // Vector-first resolution of targets, then hybrid retrieval
            log.debug("Using hybrid strategy with GraphQuery targets");
            return resolveEntryPoints(request.graphQuery(), request.topK())
                    .flatMap(startNodeIds -> {
                        if (startNodeIds.isEmpty()) {
                            log.warn("No entry points resolved from GraphQuery. Falling back to vector-only.");
                            return vectorOnly(request.query(), semanticKeywords, request.topK())
                                    .doOnSuccess(result -> result
                                            .setRetrievalTimeMs(System.currentTimeMillis() - startTime));
                        }

                        int depth = request.graphQuery().traversal() != null
                                ? request.graphQuery().traversal().maxDepth()
                                : 2;

                        return graphStore.extractSubgraph(startNodeIds, depth)
                                .map(subgraph -> enforceNodeLimit(subgraph, request.maxGraphNodes(), startNodeIds))
                                .flatMap(subgraph -> hybridRetrieve(subgraph, request.query(), semanticKeywords,
                                        request.topK()))
                                .doOnSuccess(result -> result
                                        .setRetrievalTimeMs(System.currentTimeMillis() - startTime));
                    });
        }

        // No graph query provided – pure vector path with optional semantic keywords
        return vectorOnly(request.query(), semanticKeywords, request.topK())
                .doOnSuccess(result -> result.setRetrievalTimeMs(System.currentTimeMillis() - startTime));
    }

    @Override
    public Mono<Node> findNode(UUID id) {
        return graphStore.getNode(id);
    }

    @Override
    public Flux<Node> findByLabel(String label) {
        return graphStore.findNodesByLabel(label);
    }

    @Override
    public Flux<Node> findRelated(UUID id, String relationType, Direction direction) {
        if (direction == Direction.OUTGOING) {
            return graphStore.getNeighborsByRelationType(id, relationType);
        }
        // GraphStore interface currently only supports outgoing/undirected in basic
        // methods
        // For full direction support, we might need to expand GraphStore or use
        // getNeighbors and filter
        // For now, defaulting to getNeighborsByRelationType which is usually outgoing
        return graphStore.getNeighborsByRelationType(id, relationType);
    }

    @Override
    public Mono<List<Node>> findNodesByProperties(String label, String propertyName, Object propertyValue) {
        return graphStore.findNodesByLabel(label)
                .filter(node -> {
                    if (node.getProperties() == null) {
                        return false;
                    }
                    Object value = node.getProperties().get(propertyName);
                    return value != null && value.equals(propertyValue);
                })
                .collectList();
    }

    @Override
    public Mono<RetrievalResult> expandFromSeeds(List<UUID> seedNodeIds, int depth, List<String> relationTypes) {
        log.debug("Expanding from {} seed nodes with depth={}", seedNodeIds.size(), depth);

        return graphStore.extractSubgraph(seedNodeIds, depth)
                .map(subgraph -> {
                    // Filter by relation types if specified
                    List<Edge> filteredEdges = subgraph.getEdges();
                    if (relationTypes != null && !relationTypes.isEmpty()) {
                        filteredEdges = subgraph.getEdges().stream()
                                .filter(e -> relationTypes.contains(e.getRelationType()))
                                .collect(Collectors.toList());
                    }

                    return new RetrievalResult(
                            subgraph.getNodes(),
                            filteredEdges,
                            List.of(),
                            RetrievalResult.RetrievalStrategy.EXPANSION);
                });
    }

    private Mono<RetrievalResult> retrieveFromVectorStore(VectorRetrievalRequest request) {
        return retrieveFromVectorStore(request, List.of());
    }

    private Mono<RetrievalResult> retrieveFromVectorStore(VectorRetrievalRequest request, List<String> extraKeywords) {
        log.debug("Vector-only retrieval: query={}, topK={}", request.getQuery(), request.getTopK());

        String enhancedQuery = buildEnhancedQuery(request.getQuery(), extraKeywords);

        return generateQueryEmbedding(enhancedQuery)
                .flatMap(embedding -> findSimilarChunks(embedding, request.getTopK()))
                .map(chunks -> new RetrievalResult(
                        List.of(),
                        List.of(),
                        chunks,
                        RetrievalResult.RetrievalStrategy.VECTOR_ONLY));
    }

    /**
     * Resolve entry points (UUIDs) from GraphQuery targets using vector search.
     * Implements ADR-002 Vector-First Resolution:
     * 1. Embed target description for semantic matching
     * 2. Find similar chunks via vector search
     * 3. Get linked node IDs from chunks
     * 4. Filter nodes by typeHint (label) to prevent context explosion
     * 5. Return only relevant entry point node UUIDs
     */
    private Mono<List<UUID>> resolveEntryPoints(GraphQuery graphQuery, int topK) {
        return Flux.fromIterable(graphQuery.targets())
                .flatMap(target -> {
                    String queryText = target.description();
                    if (queryText == null || queryText.isEmpty()) {
                        return Mono.empty();
                    }

                    String typeHint = target.typeHint();

                    return generateQueryEmbedding(queryText)
                            .flatMapMany(embedding -> chunkStore.findTopKSimilar(embedding, topK))
                            .doOnNext(chunk -> log.debug("Found chunk: id={}, linkedNodeId={}, content preview={}",
                                    chunk.getId(), chunk.getLinkedNodeId(),
                                    chunk.getContent() != null && chunk.getContent().length() > 50
                                            ? chunk.getContent().substring(0, 50) + "..."
                                            : chunk.getContent()))
                            .filter(chunk -> chunk.getLinkedNodeId() != null)
                            .map(Chunk::getLinkedNodeId)
                            .distinct()
                            .flatMap(nodeId -> {
                                // Filter by typeHint if provided
                                if (typeHint == null || typeHint.isEmpty()) {
                                    log.debug("No typeHint filter, accepting node: {}", nodeId);
                                    return Flux.just(nodeId);
                                }

                                // Look up node and check if label matches typeHint
                                return graphStore.getNode(nodeId)
                                        .flatMapMany(node -> {
                                            boolean directMatch = typeHint.equals(node.getLabel());
                                            log.debug("Found node: id={}, label='{}', typeHint='{}', match={}",
                                                    node.getId(), node.getLabel(), typeHint, directMatch);

                                            if (directMatch) {
                                                return Flux.just(node.getId());
                                            }

                                            log.debug(
                                                    "Node {} label mismatch ({} vs {}). Searching neighbors for typeHint match.",
                                                    nodeId, node.getLabel(), typeHint);

                                            return graphStore.getNeighbors(nodeId)
                                                    .doOnNext(neighbor -> log.debug(
                                                            "Neighbor candidate: id={}, label='{}'", neighbor.getId(),
                                                            neighbor.getLabel()))
                                                    .filter(neighbor -> typeHint.equals(neighbor.getLabel()))
                                                    .map(Node::getId)
                                                    .take(topK)
                                                    .switchIfEmpty(Flux.defer(() -> {
                                                        log.debug(
                                                                "No neighbors matching typeHint='{}' found within 1 hop of node {}",
                                                                typeHint, nodeId);
                                                        return Flux.<UUID>empty();
                                                    }));
                                        })
                                        .switchIfEmpty(Flux.defer(() -> {
                                            log.warn("Node {} not found in graph store!", nodeId);
                                            return Flux.<UUID>empty();
                                        }));
                            })
                            .collectList();
                })
                .flatMapIterable(list -> list)
                .distinct()
                .collectList()
                .doOnSuccess(nodeIds -> {
                    if (nodeIds != null && !nodeIds.isEmpty()) {
                        log.debug("Resolved {} entry points from {} targets",
                                nodeIds.size(), graphQuery.targets().size());
                    }
                });
    }

    /**
     * Generate query embedding using configured embedding model.
     */
    private Mono<float[]> generateQueryEmbedding(String query) {
        return Mono.fromCallable(() -> {
            log.debug("Calling embedding model: class={}, query='{}'",
                    embeddingModel.getClass().getName(), query);
            var response = embeddingModel.call(new org.springframework.ai.embedding.EmbeddingRequest(
                    java.util.List.of(query), null));
            if (response == null || response.getResults().isEmpty()) {
                throw new IllegalStateException("Embedding model returned null or empty response");
            }
            return response.getResults().get(0).getOutput();
        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Failed to generate query embedding", e);
                    return Mono.error(new RuntimeException("Failed to generate query embedding", e));
                });
    }

    /**
     * Find similar chunks using database-native vector similarity.
     */
    private Mono<List<Chunk>> findSimilarChunks(float[] queryEmbedding, int topK) {
        return chunkStore.findTopKSimilar(queryEmbedding, topK)
                .collectList();
    }

    /**
     * Enforce maximum node limit on subgraph using BFS to prioritize relevant
     * nodes.
     */
    private GraphSubgraph enforceNodeLimit(GraphSubgraph subgraph, int maxNodes, List<UUID> seedIds) {
        if (subgraph.getNodes().size() <= maxNodes) {
            return subgraph;
        }

        log.warn("Graph subgraph exceeded max nodes: {} > {}. Truncating using BFS from seeds.",
                subgraph.getNodes().size(), maxNodes);

        // Build adjacency list for BFS
        Map<UUID, Set<UUID>> adjacency = new HashMap<>();
        for (Edge edge : subgraph.getEdges()) {
            adjacency.computeIfAbsent(edge.getSourceNodeId(), k -> new HashSet<>()).add(edge.getTargetNodeId());
            adjacency.computeIfAbsent(edge.getTargetNodeId(), k -> new HashSet<>()).add(edge.getSourceNodeId());
        }

        Set<UUID> keptNodeIds = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>(seedIds);
        keptNodeIds.addAll(seedIds);

        // BFS to collect most relevant nodes
        while (!queue.isEmpty() && keptNodeIds.size() < maxNodes) {
            UUID currentId = queue.poll();
            if (adjacency.containsKey(currentId)) {
                for (UUID neighborId : adjacency.get(currentId)) {
                    if (!keptNodeIds.contains(neighborId)) {
                        keptNodeIds.add(neighborId);
                        queue.add(neighborId);
                        if (keptNodeIds.size() >= maxNodes) {
                            break;
                        }
                    }
                }
            }
        }

        // If we still have space, fill with remaining nodes
        if (keptNodeIds.size() < maxNodes) {
            subgraph.getNodes().stream()
                    .map(Node::getId)
                    .filter(id -> !keptNodeIds.contains(id))
                    .limit(maxNodes - keptNodeIds.size())
                    .forEach(keptNodeIds::add);
        }

        List<Node> truncatedNodes = subgraph.getNodes().stream()
                .filter(n -> keptNodeIds.contains(n.getId()))
                .collect(Collectors.toList());

        List<Edge> truncatedEdges = subgraph.getEdges().stream()
                .filter(e -> keptNodeIds.contains(e.getSourceNodeId()) && keptNodeIds.contains(e.getTargetNodeId()))
                .collect(Collectors.toList());

        return new GraphSubgraph(truncatedNodes, truncatedEdges);
    }

    private Mono<RetrievalResult> hybridRetrieve(GraphSubgraph subgraph, String baseQuery,
            List<String> semanticKeywords, int topK) {
        Set<UUID> contextNodeIds = subgraph.getNodes().stream()
                .map(Node::getId)
                .collect(Collectors.toSet());

        List<String> graphKeywords = extractKeywordsFromGraph(subgraph.getNodes(), subgraph.getEdges());
        List<String> mergedKeywords = new ArrayList<>();
        mergedKeywords.addAll(semanticKeywords);
        mergedKeywords.addAll(graphKeywords);

        String enhancedQuery = buildEnhancedQuery(baseQuery, mergedKeywords);

        int oversample = Math.max(topK * 2, topK + 5);
        return generateQueryEmbedding(enhancedQuery)
                .flatMap(embedding -> findSimilarChunks(embedding, oversample))
                .map(chunks -> {
                    List<Chunk> prioritized = prioritizeChunks(chunks, contextNodeIds, topK);
                    boolean usedGraph = prioritized.stream()
                            .anyMatch(chunk -> chunk.getLinkedNodeId() != null
                                    && contextNodeIds.contains(chunk.getLinkedNodeId()));
                    RetrievalResult.RetrievalStrategy strategy = usedGraph
                            ? RetrievalResult.RetrievalStrategy.HYBRID
                            : RetrievalResult.RetrievalStrategy.VECTOR_ONLY;

                    return new RetrievalResult(subgraph.getNodes(), subgraph.getEdges(), prioritized, strategy);
                });
    }

    private List<Chunk> prioritizeChunks(List<Chunk> chunks, Set<UUID> contextNodeIds, int topK) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        if (contextNodeIds == null || contextNodeIds.isEmpty()) {
            return chunks.stream().limit(topK).toList();
        }

        List<Chunk> inContext = new ArrayList<>();
        List<Chunk> outOfContext = new ArrayList<>();

        for (Chunk chunk : chunks) {
            UUID linked = chunk.getLinkedNodeId();
            if (linked != null && contextNodeIds.contains(linked)) {
                inContext.add(chunk);
            } else {
                outOfContext.add(chunk);
            }
        }

        List<Chunk> merged = new ArrayList<>();
        merged.addAll(inContext);
        merged.addAll(outOfContext);

        return merged.stream().limit(topK).toList();
    }

    /**
     * Execute pattern-based retrieval: run graph patterns, extract nodes, then get
     * linked chunks.
     */
    private Mono<RetrievalResult> executePatternBasedRetrieval(RetrievalRequest request, long startTime) {
        org.ddse.ml.cef.dto.GraphQuery graphQuery = request.graphQuery();

        // First, resolve entry points from targets
        return resolveEntryPoints(graphQuery, request.topK())
                .flatMap(entryPoints -> {
                    if (entryPoints.isEmpty()) {
                        log.warn("No entry points resolved for patterns. Falling back to vector search.");
                        return vectorOnly(request.query(),
                                request.semanticKeywords() != null ? request.semanticKeywords() : List.of(),
                                request.topK());
                    }

                    log.info("Resolved {} entry points for pattern execution", entryPoints.size());

                    // Execute patterns
                    Set<UUID> entrySet = new HashSet<>(entryPoints);
                    org.ddse.ml.cef.dto.RankingStrategy ranking = graphQuery.rankingStrategy() != null
                            ? graphQuery.rankingStrategy()
                            : org.ddse.ml.cef.dto.RankingStrategy.PATH_LENGTH;

                    return Flux.fromIterable(graphQuery.patterns())
                            .flatMap(pattern -> patternExecutor.executePattern(pattern, entrySet, request.topK(), ranking))
                            .collectList()
                            .flatMap(matchedPaths -> {
                                if (matchedPaths.isEmpty()) {
                                    log.warn("Pattern execution returned no paths. Falling back to vector search.");
                                    return vectorOnly(request.query(),
                                            request.semanticKeywords() != null ? request.semanticKeywords() : List.of(),
                                            request.topK());
                                }

                                log.info("Pattern execution found {} paths", matchedPaths.size());

                                // Extract all node IDs from matched paths
                                Set<UUID> nodeIds = matchedPaths.stream()
                                        .flatMap(path -> path.nodeIds().stream())
                                        .collect(Collectors.toSet());

                                log.info("Extracting {} unique nodes from matched paths", nodeIds.size());

                                // Get nodes and edges from graph
                                return graphStore.extractSubgraph(new ArrayList<>(nodeIds), 0) // depth=0, we already
                                                                                               // have the nodes
                                        .flatMap(subgraph -> {
                                            // Get chunks linked to these nodes
                                            return Flux.fromIterable(nodeIds)
                                                    .flatMap(nodeId -> chunkStore.findByLinkedNodeId(nodeId))
                                                    .collectList()
                                                    .map(chunks -> {
                                                        log.info("Found {} chunks linked to pattern nodes",
                                                                chunks.size());
                                                        RetrievalResult result = new RetrievalResult(
                                                                subgraph.getNodes(),
                                                                subgraph.getEdges(),
                                                                chunks,
                                                                RetrievalResult.RetrievalStrategy.HYBRID);
                                                        result.setRetrievalTimeMs(
                                                                System.currentTimeMillis() - startTime);
                                                        return result;
                                                    });
                                        });
                            });
                });
    }

    private boolean hasGraphTargets(RetrievalRequest request) {
        return request.graphQuery() != null
                && request.graphQuery().targets() != null
                && !request.graphQuery().targets().isEmpty();
    }

    private String buildEnhancedQuery(String baseQuery, List<String> keywords) {
        if (baseQuery == null) {
            baseQuery = "";
        }
        if (keywords == null || keywords.isEmpty()) {
            return baseQuery;
        }

        String keywordString = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(" "));

        if (keywordString.isEmpty()) {
            return baseQuery;
        }
        return (baseQuery + " " + keywordString).trim();
    }

    private List<String> extractKeywordsFromGraph(List<Node> nodes, List<Edge> edges) {
        Set<String> keywords = new HashSet<>();

        for (Node node : nodes) {
            keywords.add(node.getLabel());
            if (node.getProperties() != null) {
                for (Object value : node.getProperties().values()) {
                    if (value == null) {
                        continue;
                    }
                    String str = String.valueOf(value).trim();
                    if (str.length() > 2 && !str.matches("^[0-9a-fA-F-]{36}$")) {
                        keywords.add(str);
                    }
                }
            }
        }

        for (Edge edge : edges) {
            if (edge.getRelationType() != null) {
                keywords.add(edge.getRelationType());
            }
        }

        return new ArrayList<>(keywords);
    }

    private Mono<RetrievalResult> vectorOnly(String query, List<String> semanticKeywords, int topK) {
        return retrieveFromVectorStore(new VectorRetrievalRequest(query, topK), semanticKeywords);
    }
}
