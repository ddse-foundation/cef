package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reactive adapter wrapping InMemoryKnowledgeGraph to implement GraphStore
 * interface.
 * Bridges synchronous JGraphT operations with reactive Project Reactor.
 * 
 * Thread-safety: Delegates to thread-safe InMemoryKnowledgeGraph.
 * Reactive: All blocking operations run on boundedElastic scheduler.
 * 
 * NOTE: This class is NOT a @Component. It is created by GraphStoreAutoConfiguration
 * when cef.graph.store=in-memory or when no specific graph store is configured.
 * 
 * @author mrmanna
 */
public class InMemoryGraphStore implements GraphStore {

    private final InMemoryKnowledgeGraph graph;

    public InMemoryGraphStore(InMemoryKnowledgeGraph graph) {
        this.graph = graph;
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        // InMemoryKnowledgeGraph doesn't need explicit initialization
        // Relation types are validated on addEdge
        return Mono.empty();
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return Mono.fromRunnable(() -> graph.addNode(node))
                .thenReturn(node)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return Mono.fromRunnable(() -> graph.addEdge(edge))
                .thenReturn(edge)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return Mono.fromCallable(() -> graph.findNode(nodeId).orElse(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return Mono.<Edge>fromCallable(() -> {
            // InMemoryKnowledgeGraph doesn't have direct edge lookup by ID
            // Would need to scan all edges - not implemented in base graph
            return null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        return Flux.fromIterable(graph.findNodesByLabel(label))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        // InMemoryKnowledgeGraph doesn't have this method
        // Would need to scan all edges by type
        return Flux.empty();
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        return Flux.fromIterable(graph.getNeighbors(nodeId, 1))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        // InMemoryKnowledgeGraph doesn't have this method
        // Would need filtering by relation type
        return Flux.empty();
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.<List<UUID>>fromCallable(() -> {
            var pathResult = graph.findShortestPath(sourceId, targetId);
            return pathResult.map(result -> result.nodeIds()).orElse(List.of());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        return Flux.fromIterable(graph.getNeighbors(nodeId, depth))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        return Mono.fromCallable(() -> {
            // Collect all nodes within k-hops
            Set<Node> nodes = new HashSet<>();
            Set<Edge> edges = new HashSet<>();

            for (UUID seedId : seedNodeIds) {
                // Add seed node
                graph.findNode(seedId).ifPresent(nodes::add);

                // Get k-hop neighbors
                nodes.addAll(graph.getNeighbors(seedId, depth));

                // Collect edges between nodes
                for (Node node : nodes) {
                    edges.addAll(graph.getEdges(node.getId()));
                }
            }

            return new GraphSubgraph(List.copyOf(nodes), List.copyOf(edges));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return Mono.fromRunnable(() -> graph.removeNode(nodeId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return Mono.fromRunnable(() -> graph.removeEdge(edgeId))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> graph.clear())
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.<GraphStats>fromCallable(() -> {
            long nodeCount = graph.getNodeCount();
            long edgeCount = graph.getEdgeCount();
            Map<String, Long> labelCounts = graph.getLabelCounts();
            // InMemoryKnowledgeGraph doesn't track edge types separately
            Map<String, Long> edgeTypeCounts = new HashMap<>();
            double avgDegree = nodeCount > 0 ? (double) edgeCount / nodeCount : 0.0;
            return new GraphStats(nodeCount, edgeCount, labelCounts, edgeTypeCounts, avgDegree);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return Flux.fromIterable(nodes)
                .flatMap(this::addNode)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return Flux.fromIterable(edges)
                .flatMap(this::addEdge)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> getEdgesForNode(UUID nodeId) {
        return Flux.fromIterable(graph.getEdges(nodeId))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
