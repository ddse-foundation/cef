package org.ddse.ml.cef.retriever;

import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.*;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of ContextRetriever.
 * 
 * Implements 3-level fallback strategy:
 * 1. Graph-only (when graphHints provided)
 * 2. Hybrid (semantic search + graph expansion)
 * 3. Vector-only (fallback if graph empty)
 *
 * @author mrmanna
 */
@Service
public class DefaultContextRetriever implements ContextRetriever {

    private static final Logger log = LoggerFactory.getLogger(DefaultContextRetriever.class);

    private final GraphStore graphStore;
    private final ChunkStore chunkStore;
    private final EmbeddingModel embeddingModel;
    private final CefProperties properties;
    private final org.ddse.ml.cef.service.PatternExecutor patternExecutor;

    public DefaultContextRetriever(GraphStore graphStore,
            ChunkStore chunkStore,
            EmbeddingModel embeddingModel,
            CefProperties properties,
            org.ddse.ml.cef.service.PatternExecutor patternExecutor) {
        this.graphStore = graphStore;
        this.chunkStore = chunkStore;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.patternExecutor = patternExecutor;
    }

    @Override
    public Mono<RetrievalResult> retrieveContext(RetrievalRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("=== [ContextRetriever] Starting Retrieval ===");
        log.info("Request: {}", request);

        // Pattern-based traversal if patterns are provided
        if (request.graphQuery() != null && request.graphQuery().usePatternsForTraversal()) {
            log.info("Strategy: PATTERN_BASED_TRAVERSAL (GraphQuery with patterns provided)");
            log.info("Patterns: {}", request.graphQuery().patterns().size());
            return executePatternBasedRetrieval(request, startTime);
        }

        if (request.graphQuery() != null && request.graphQuery().targets() != null
                && !request.graphQuery().targets().isEmpty()) {
            // New Vector-First Strategy
            log.info("Strategy: VECTOR_FIRST_RESOLUTION (GraphQuery provided)");
            log.info("Targets: {}", request.graphQuery().targets());

            return resolveEntryPoints(request.graphQuery(), request.topK())
                    .flatMap(startNodeIds -> {
                        if (startNodeIds.isEmpty()) {
                            log.warn("!!! No entry points resolved from GraphQuery. Fallback to vector search. !!!");
                            return retrieveFromVectorStore(
                                    new VectorRetrievalRequest(request.query(), request.topK()));
                        }
                        log.info("Resolved {} entry points: {}", startNodeIds.size(), startNodeIds);
                        log.info("Extracting subgraph with depth: {}",
                                request.graphQuery().traversal() != null ? request.graphQuery().traversal().maxDepth()
                                        : "2 (default)");

                        int depth = request.graphQuery().traversal() != null
                                ? request.graphQuery().traversal().maxDepth()
                                : 2;
                        return graphStore.extractSubgraph(startNodeIds, depth)
                                .map(subgraph -> {
                                    log.info("Subgraph extracted: {} nodes, {} edges", subgraph.getNodes().size(),
                                            subgraph.getEdges().size());
                                    return enforceNodeLimit(subgraph, request.maxGraphNodes(), startNodeIds);
                                })
                                .map(subgraph -> new RetrievalResult(subgraph.getNodes(), subgraph.getEdges(),
                                        List.of(), RetrievalResult.RetrievalStrategy.GRAPH_ONLY));
                    })
                    .doOnSuccess(result -> {
                        result.setRetrievalTimeMs(System.currentTimeMillis() - startTime);
                        log.info("=== [ContextRetriever] Finished. Strategy: {}, Nodes: {}, Edges: {}, Chunks: {} ===",
                                result.getStrategy(), result.getNodes().size(), result.getEdges().size(),
                                result.getChunks().size());
                    });
        } else {
            // Fallback to simple vector search if no graph query
            log.info("Strategy: VECTOR_ONLY (No GraphQuery provided)");
            return retrieveFromVectorStore(new VectorRetrievalRequest(request.query(), request.topK()))
                    .doOnSuccess(result -> {
                        result.setRetrievalTimeMs(System.currentTimeMillis() - startTime);
                        log.info("=== [ContextRetriever] Finished. Strategy: {}, Chunks: {} ===",
                                result.getStrategy(), result.getChunks().size());
                    });
        }
    }

    @Override
    public Mono<RetrievalResult> retrieveFromGraph(GraphRetrievalRequest request) {
        log.debug("Graph-only retrieval: startLabels={}, relationTypes={}, depth={}",
                request.getStartNodeLabels(), request.getRelationTypes(), request.getDepth());

        // Find all nodes matching start labels
        return Flux.fromIterable(request.getStartNodeLabels())
                .flatMap(graphStore::findNodesByLabel)
                .filter(node -> {
                    if (request.getNodeFilters() == null || request.getNodeFilters().isEmpty()) {
                        return true;
                    }
                    if (node.getProperties() == null) {
                        return false;
                    }
                    for (Map.Entry<String, Object> entry : request.getNodeFilters().entrySet()) {
                        Object propValue = node.getProperties().get(entry.getKey());
                        if (!isFuzzyMatch(propValue, entry.getValue())) {
                            return false;
                        }
                    }
                    return true;
                })
                .collectList()
                .flatMap(startNodes -> {
                    if (startNodes.isEmpty()) {
                        log.warn("No start nodes found for labels: {}", request.getStartNodeLabels());
                        return Mono.just(new RetrievalResult(List.of(), List.of(), List.of(),
                                RetrievalResult.RetrievalStrategy.GRAPH_ONLY));
                    }

                    List<UUID> seedIds = startNodes.stream()
                            .map(Node::getId)
                            .collect(Collectors.toList());

                    // Extract subgraph
                    return graphStore.extractSubgraph(seedIds, request.getDepth())
                            .map(subgraph -> {
                                // Filter edges by relation types if specified
                                List<Edge> filteredEdges = subgraph.getEdges();
                                if (request.getRelationTypes() != null && !request.getRelationTypes().isEmpty()) {
                                    filteredEdges = subgraph.getEdges().stream()
                                            .filter(e -> request.getRelationTypes().contains(e.getRelationType()))
                                            .collect(Collectors.toList());
                                }

                                return new RetrievalResult(
                                        subgraph.getNodes(),
                                        filteredEdges,
                                        List.of(),
                                        RetrievalResult.RetrievalStrategy.GRAPH_ONLY);
                            });
                });
    }

    @Override
    public Mono<RetrievalResult> retrieveFromVectorStore(VectorRetrievalRequest request) {
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
     * Resolve entry points (UUIDs) from GraphQuery targets using exact match and
     * vector search.
     */
    private Mono<List<UUID>> resolveEntryPoints(GraphQuery graphQuery, int topK) {
        return Flux.fromIterable(graphQuery.targets())
                .flatMap(target -> {
                    String queryText = target.description();
                    if (queryText == null || queryText.isEmpty()) {
                        return Mono.empty();
                    }

                    // 1. Try Exact Graph Match (Label)
                    Mono<List<UUID>> exactMatches = graphStore.findNodesByLabel(queryText)
                            .map(Node::getId)
                            .collectList()
                            .doOnNext(list -> {
                                if (!list.isEmpty()) {
                                    log.debug("Found {} exact matches for target '{}'", list.size(), queryText);
                                }
                            });

                    // 2. Try Type Hint + Property Match (Heuristic)
                    Mono<List<UUID>> heuristicMatches = Mono.just(Collections.<UUID>emptyList());
                    if (target.typeHint() != null && !target.typeHint().isEmpty()) {
                        heuristicMatches = graphStore.findNodesByLabel(target.typeHint())
                                .filter(node -> {
                                    if (node.getProperties() == null)
                                        return false;
                                    // Check if description contains any property value (e.g. ID)
                                    // or if any property value equals description
                                    for (Object val : node.getProperties().values()) {
                                        if (val instanceof String) {
                                            String strVal = (String) val;
                                            // Avoid matching short common words like "Male", "Low"
                                            if (strVal.length() > 3
                                                    && (queryText.contains(strVal) || strVal.equals(queryText))) {
                                                return true;
                                            }
                                        }
                                    }
                                    return false;
                                })
                                .map(Node::getId)
                                .collectList()
                                .doOnNext(list -> {
                                    if (!list.isEmpty()) {
                                        log.debug("Found {} heuristic matches for target '{}' with type '{}'",
                                                list.size(), queryText, target.typeHint());
                                    }
                                });
                    }

                    // 3. Vector Search
                    Mono<List<UUID>> vectorMatches = generateQueryEmbedding(queryText)
                            .flatMapMany(embedding -> chunkStore.findTopKSimilar(embedding, topK))
                            .filter(chunk -> chunk.getLinkedNodeId() != null)
                            .map(Chunk::getLinkedNodeId)
                            .collectList();

                    // Combine results
                    return Mono.zip(exactMatches, heuristicMatches, vectorMatches)
                            .map(tuple -> {
                                List<UUID> combined = new ArrayList<>();
                                combined.addAll(tuple.getT1());
                                combined.addAll(tuple.getT2());
                                combined.addAll(tuple.getT3());
                                return combined;
                            });
                })
                .flatMapIterable(list -> list)
                .distinct()
                .collectList();
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

    /**
     * Generate query embedding using configured embedding model.
     */
    private Mono<float[]> generateQueryEmbedding(String query) {
        return Mono.fromCallable(() -> {
            log.debug("Calling embedding model: class={}, query='{}'",
                    embeddingModel.getClass().getName(), query);
            var response = embeddingModel.call(new org.springframework.ai.embedding.EmbeddingRequest(
                    java.util.List.of(query), null));
            log.debug("Embedding model response: response={}, resultsSize={}",
                    response, response == null ? -1 : response.getResults().size());
            if (response == null || response.getResults().isEmpty()) {
                throw new IllegalStateException("Embedding model returned null or empty response");
            }
            return response.getResults().get(0).getOutput();
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(embedding -> log.debug("Generated query embedding with dimension: {}", embedding.length))
                .onErrorResume(e -> {
                    log.error("Failed to generate query embedding", e);
                    return Mono.error(new RuntimeException("Failed to generate query embedding", e));
                });
    }

    /**
     * Find similar chunks using database-native vector similarity.
     * Automatically detects PostgreSQL vs DuckDB based on configuration.
     */
    private Mono<List<Chunk>> findSimilarChunks(float[] queryEmbedding, int topK) {
        return chunkStore.findTopKSimilar(queryEmbedding, topK)
                .collectList()
                .doOnSuccess(chunks -> log.debug("Found {} similar chunks", chunks.size()));
    }

    /**
     * Check if two values match, supporting fuzzy matching for strings.
     * 
     * Logic:
     * 1. Exact match (equals)
     * 2. Case-insensitive match (String)
     * 3. Levenshtein distance <= 2 (String, for typos)
     */
    private boolean isFuzzyMatch(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }

        // 1. Exact match
        if (actual.equals(expected)) {
            return true;
        }

        // 2. String fuzzy matching
        if (actual instanceof String && expected instanceof String) {
            String s1 = (String) actual;
            String s2 = (String) expected;

            // Case-insensitive
            if (s1.equalsIgnoreCase(s2)) {
                return true;
            }

            // Levenshtein distance (allow up to 2 edits for typos)
            // Only apply if strings are reasonably long (> 3 chars) to avoid false
            // positives on short codes
            if (s1.length() > 3 && s2.length() > 3) {
                return calculateLevenshteinDistance(s1.toLowerCase(), s2.toLowerCase()) <= 2;
            }
        }

        return false;
    }

    /**
     * Calculate Levenshtein distance between two strings.
     * Standard DP implementation.
     */
    private int calculateLevenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = min(
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1),
                            dp[i - 1][j] + 1,
                            dp[i][j - 1] + 1);
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    private int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
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

        // If we still have space (e.g. disconnected components), fill with remaining
        // nodes
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

        // Filter edges to only those connecting truncated nodes
        List<Edge> truncatedEdges = subgraph.getEdges().stream()
                .filter(e -> keptNodeIds.contains(e.getSourceNodeId()) && keptNodeIds.contains(e.getTargetNodeId()))
                .collect(Collectors.toList());

        return new GraphSubgraph(truncatedNodes, truncatedEdges);
    }

    /**
     * Extract keywords from graph nodes and edges.
     * 
     * /**
     * Extract keywords from graph nodes and edges.
     * Used to enhance vector search query.
     */
    private List<String> extractKeywordsFromGraph(List<Node> nodes, List<Edge> edges) {
        Set<String> keywords = new HashSet<>();

        // Extract from nodes: labels and property values
        nodes.forEach(node -> {
            keywords.add(node.getLabel());
            if (node.getProperties() != null) {
                node.getProperties().values().forEach(value -> {
                    if (value != null) {
                        String str = String.valueOf(value);
                        // Only add meaningful keywords (skip IDs, UUIDs)
                        if (str.length() > 2 && !str.matches("^[0-9a-f-]{36}$")) {
                            keywords.add(str);
                        }
                    }
                });
            }
        });

        // Extract from edges: relationship types
        edges.forEach(edge -> keywords.add(edge.getRelationType()));

        log.debug("Extracted {} keywords from graph", keywords.size());
        return new ArrayList<>(keywords);
    }

    /**
     * Build enhanced query by combining user query with graph keywords.
     */
    private String buildEnhancedQuery(String originalQuery, List<String> graphKeywords) {
        if (graphKeywords.isEmpty()) {
            return originalQuery;
        }

        // Limit to top 10 keywords to avoid query explosion
        String keywordString = graphKeywords.stream()
                .limit(10)
                .collect(Collectors.joining(" "));

        return originalQuery + " " + keywordString;
    }

    /**
     * Execute pattern-based retrieval: run graph patterns, extract nodes, then
     * vector search on linked chunks.
     */
    private Mono<RetrievalResult> executePatternBasedRetrieval(RetrievalRequest request, long startTime) {
        GraphQuery graphQuery = request.graphQuery();

        // First, resolve entry points from targets
        return resolveEntryPoints(graphQuery, request.topK())
                .flatMap(entryPoints -> {
                    if (entryPoints.isEmpty()) {
                        log.warn("No entry points resolved. Falling back to vector search.");
                        return retrieveFromVectorStore(new VectorRetrievalRequest(request.query(), request.topK()));
                    }

                    log.info("Resolved {} entry points for pattern execution", entryPoints.size());

                    // Execute patterns (or combinator if present)
                    Mono<List<MatchedPath>> pathsMono;
                    if (graphQuery.hasCombinator()) {
                        pathsMono = executeCombinedPatterns(graphQuery, new HashSet<>(entryPoints), request.topK());
                    } else if (!graphQuery.patterns().isEmpty()) {
                        // Single pattern or multiple independent patterns
                        Set<UUID> entrySet = new HashSet<>(entryPoints);
                        RankingStrategy ranking = graphQuery.rankingStrategy() != null
                                ? graphQuery.rankingStrategy()
                                : RankingStrategy.PATH_LENGTH;

                        pathsMono = Flux.fromIterable(graphQuery.patterns())
                                .flatMap(pattern -> Mono.fromCallable(() -> patternExecutor.executePattern(pattern,
                                        entrySet, request.topK(), ranking)))
                                .flatMapIterable(list -> list)
                                .collectList();
                    } else {
                        log.warn(
                                "Pattern traversal requested but no patterns provided. Falling back to vector search.");
                        return retrieveFromVectorStore(new VectorRetrievalRequest(request.query(), request.topK()));
                    }

                    return pathsMono
                            .flatMap(matchedPaths -> {
                                if (matchedPaths.isEmpty()) {
                                    log.warn("Pattern execution returned no paths. Falling back to vector search.");
                                    return retrieveFromVectorStore(
                                            new VectorRetrievalRequest(request.query(), request.topK()));
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
                                            // Get chunks linked to these nodes for vector search
                                            return Flux.fromIterable(nodeIds)
                                                    .flatMap(nodeId -> chunkStore.findByLinkedNodeId(nodeId))
                                                    .collectList()
                                                    .map(chunks -> {
                                                        log.info("Found {} chunks linked to pattern nodes",
                                                                chunks.size());
                                                        return new RetrievalResult(
                                                                subgraph.getNodes(),
                                                                subgraph.getEdges(),
                                                                chunks,
                                                                RetrievalResult.RetrievalStrategy.HYBRID);
                                                    });
                                        });
                            });
                })
                .doOnSuccess(result -> {
                    result.setRetrievalTimeMs(System.currentTimeMillis() - startTime);
                    log.info(
                            "=== [ContextRetriever] Finished Pattern-Based Retrieval. Nodes: {}, Edges: {}, Chunks: {} ===",
                            result.getNodes().size(), result.getEdges().size(), result.getChunks().size());
                });
    }

    /**
     * Execute multiple patterns with combinator logic (INTERSECTION, UNION,
     * SEQUENTIAL).
     */
    private Mono<List<MatchedPath>> executeCombinedPatterns(GraphQuery graphQuery, Set<UUID> entryPoints,
            int maxPaths) {
        QueryCombinator combinator = graphQuery.combinator();
        List<GraphPattern> patterns = graphQuery.patterns();
        RankingStrategy ranking = graphQuery.rankingStrategy() != null
                ? graphQuery.rankingStrategy()
                : RankingStrategy.PATH_LENGTH;

        return Flux.fromIterable(patterns)
                .flatMap(pattern -> Mono
                        .fromCallable(() -> patternExecutor.executePattern(pattern, entryPoints, maxPaths, ranking)))
                .collectList()
                .map(allResults -> {
                    switch (combinator.type()) {
                        case INTERSECTION:
                            // Only paths with nodes present in ALL pattern results
                            return intersectPaths(allResults);
                        case UNION:
                            // All paths from all patterns
                            return allResults.stream()
                                    .flatMap(List::stream)
                                    .collect(Collectors.toList());
                        case SEQUENTIAL:
                            // Execute patterns in order, feed results forward (TODO: implement properly)
                            log.warn("SEQUENTIAL combinator not yet fully implemented, using UNION");
                            return allResults.stream()
                                    .flatMap(List::stream)
                                    .collect(Collectors.toList());
                        default:
                            return Collections.emptyList();
                    }
                });
    }

    /**
     * Intersection: only return paths where nodes appear in all pattern results.
     */
    private List<MatchedPath> intersectPaths(List<List<MatchedPath>> allResults) {
        if (allResults.isEmpty())
            return Collections.emptyList();

        // Get node IDs from first result
        Set<UUID> commonNodes = allResults.get(0).stream()
                .flatMap(path -> path.nodeIds().stream())
                .collect(Collectors.toSet());

        // Intersect with other results
        for (int i = 1; i < allResults.size(); i++) {
            Set<UUID> currentNodes = allResults.get(i).stream()
                    .flatMap(path -> path.nodeIds().stream())
                    .collect(Collectors.toSet());
            commonNodes.retainAll(currentNodes);
        }

        // Filter paths to only those containing common nodes
        Set<UUID> finalCommon = commonNodes;
        return allResults.get(0).stream()
                .filter(path -> path.nodeIds().stream().anyMatch(finalCommon::contains))
                .collect(Collectors.toList());
    }

    /**
     * Merge graph and vector results into single result.
     */
    private RetrievalResult mergeResults(List<Node> nodes, List<Edge> edges, List<Chunk> chunks) {
        return new RetrievalResult(
                nodes,
                edges,
                chunks,
                RetrievalResult.RetrievalStrategy.HYBRID);
    }
}
