package org.ddse.ml.cef.service;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.repository.ChunkStore;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.ddse.ml.cef.retriever.VectorRetrievalRequest;
import org.ddse.ml.cef.storage.GraphStore;
import org.ddse.ml.cef.storage.GraphSubgraph;
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

    public KnowledgeRetrieverImpl(GraphStore graphStore,
            ChunkStore chunkStore,
            EmbeddingModel embeddingModel,
            CefProperties properties) {
        this.graphStore = graphStore;
        this.chunkStore = chunkStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public Mono<RetrievalResult> retrieve(RetrievalRequest request) {
        long startTime = System.currentTimeMillis();

        log.debug("Retrieving context for request: {}", request);

        if (request.graphQuery() != null && request.graphQuery().targets() != null
                && !request.graphQuery().targets().isEmpty()) {
            // New Vector-First Strategy
            log.debug("Using Vector-First Strategy with GraphQuery");
            return resolveEntryPoints(request.graphQuery(), request.topK())
                    .flatMap(startNodeIds -> {
                        if (startNodeIds.isEmpty()) {
                            log.warn("No entry points resolved from GraphQuery. Fallback to vector search.");
                            return retrieveFromVectorStore(
                                    new VectorRetrievalRequest(request.query(), request.topK()));
                        }
                        log.debug("Resolved {} entry points. Extracting subgraph.", startNodeIds.size());
                        int depth = request.graphQuery().traversal() != null
                                ? request.graphQuery().traversal().maxDepth()
                                : 2;
                        return graphStore.extractSubgraph(startNodeIds, depth)
                                .map(subgraph -> enforceNodeLimit(subgraph, request.maxGraphNodes(), startNodeIds))
                                .map(subgraph -> new RetrievalResult(subgraph.getNodes(), subgraph.getEdges(),
                                        List.of(), RetrievalResult.RetrievalStrategy.GRAPH_ONLY));
                    })
                    .doOnSuccess(result -> result.setRetrievalTimeMs(System.currentTimeMillis() - startTime));
        } else {
            // Fallback to simple vector search if no graph query
            log.debug("No GraphQuery provided. Using Vector-Only Strategy.");
            return retrieveFromVectorStore(new VectorRetrievalRequest(request.query(), request.topK()))
                    .doOnSuccess(result -> result.setRetrievalTimeMs(System.currentTimeMillis() - startTime));
        }
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
        log.debug("Vector-only retrieval: query={}, topK={}", request.getQuery(), request.getTopK());

        return generateQueryEmbedding(request.getQuery())
                .flatMap(embedding -> findSimilarChunks(embedding, request.getTopK()))
                .map(chunks -> new RetrievalResult(
                        List.of(),
                        List.of(),
                        chunks,
                        RetrievalResult.RetrievalStrategy.VECTOR_ONLY));
    }

    /**
     * Resolve entry points (UUIDs) from GraphQuery targets using vector search.
     */
    private Mono<List<UUID>> resolveEntryPoints(GraphQuery graphQuery, int topK) {
        return Flux.fromIterable(graphQuery.targets())
                .flatMap(target -> {
                    String queryText = target.description();
                    if (queryText == null || queryText.isEmpty()) {
                        return Mono.empty();
                    }
                    return generateQueryEmbedding(queryText)
                            .flatMapMany(embedding -> chunkStore.findTopKSimilar(embedding, topK))
                            .filter(chunk -> chunk.getLinkedNodeId() != null)
                            .map(Chunk::getLinkedNodeId)
                            .collectList();
                })
                .flatMapIterable(list -> list)
                .distinct()
                .collectList();
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
}
