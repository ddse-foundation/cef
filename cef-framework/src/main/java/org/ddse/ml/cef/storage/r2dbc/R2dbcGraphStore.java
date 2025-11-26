package org.ddse.ml.cef.storage.r2dbc;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.repository.EdgeRepository;
import org.ddse.ml.cef.repository.NodeRepository;
import org.ddse.ml.cef.storage.GraphStats;
import org.ddse.ml.cef.storage.GraphStore;
import org.ddse.ml.cef.storage.GraphSubgraph;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * R2DBC implementation of GraphStore.
 * Supports PostgreSQL via Spring Data R2DBC repositories.
 * NOTE: DuckDB does not have R2DBC driver - use DuckDbGraphStore instead.
 * 
 * @author mrmanna
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "cef.database.type", havingValue = "postgresql")
public class R2dbcGraphStore implements GraphStore {

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    public R2dbcGraphStore(NodeRepository nodeRepository, EdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        return Mono.empty();
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return nodeRepository.save(node);
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return edgeRepository.save(edge);
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return nodeRepository.findById(nodeId);
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return edgeRepository.findById(edgeId);
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        return nodeRepository.findByLabel(label);
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        return edgeRepository.findByRelationType(relationType);
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        return edgeRepository.findByNodeId(nodeId)
                .flatMap(edge -> {
                    UUID neighborId = edge.getSourceNodeId().equals(nodeId) ? edge.getTargetNodeId()
                            : edge.getSourceNodeId();
                    return nodeRepository.findById(neighborId);
                })
                .distinct();
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        return edgeRepository.findByNodeId(nodeId)
                .filter(edge -> edge.getRelationType().equals(relationType))
                .flatMap(edge -> {
                    UUID neighborId = edge.getSourceNodeId().equals(nodeId) ? edge.getTargetNodeId()
                            : edge.getSourceNodeId();
                    return nodeRepository.findById(neighborId);
                })
                .distinct();
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        // BFS implementation for shortest path
        // Note: This is not efficient for large graphs in R2DBC, but sufficient for
        // benchmark scale
        return Mono.error(new UnsupportedOperationException("Shortest path not implemented for R2DBC yet"));
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        if (depth <= 0)
            return Flux.empty();

        // Simple BFS
        return getNeighbors(nodeId)
                .expandDeep(node -> {
                    // This expand is infinite, we need to control depth.
                    // Reactor's expand doesn't easily support depth control without a
                    // tuple/wrapper.
                    // For simplicity in this benchmark, we'll just do 1-hop or implement a
                    // recursive function.
                    return Flux.empty();
                })
                // Fallback to iterative approach for controlled depth
                .collectList()
                .flatMapMany(firstHop -> {
                    if (depth == 1)
                        return Flux.fromIterable(firstHop);

                    Set<UUID> visited = new HashSet<>();
                    visited.add(nodeId);
                    firstHop.forEach(n -> visited.add(n.getId()));

                    return Flux.fromIterable(firstHop)
                            .flatMap(n -> findKHopRecursive(n.getId(), depth - 1, visited));
                });
    }

    private Flux<Node> findKHopRecursive(UUID nodeId, int remainingDepth, Set<UUID> visited) {
        if (remainingDepth <= 0)
            return Flux.empty();

        return getNeighbors(nodeId)
                .filter(n -> visited.add(n.getId()))
                .flatMap(n -> Flux.just(n).concatWith(findKHopRecursive(n.getId(), remainingDepth - 1, visited)));
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        // Naive implementation: Fetch all k-hop neighbors and their edges
        Set<Node> nodes = new HashSet<>();
        Set<Edge> edges = new HashSet<>();

        return Flux.fromIterable(seedNodeIds)
                .flatMap(seedId -> {
                    return nodeRepository.findById(seedId)
                            .doOnNext(nodes::add)
                            .flatMapMany(seed -> findKHopNeighbors(seedId, depth))
                            .doOnNext(nodes::add);
                })
                .then(Mono.defer(() -> {
                    // Fetch all edges for the collected nodes
                    List<UUID> nodeIds = nodes.stream().map(Node::getId).collect(Collectors.toList());
                    return Flux.fromIterable(nodeIds)
                            .flatMap(edgeRepository::findByNodeId)
                            .doOnNext(edges::add)
                            .then();
                }))
                .thenReturn(new GraphSubgraph(new ArrayList<>(nodes), new ArrayList<>(edges)));
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return edgeRepository.deleteByNodeId(nodeId)
                .then(nodeRepository.deleteById(nodeId));
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return edgeRepository.deleteById(edgeId);
    }

    @Override
    public Mono<Void> clear() {
        return edgeRepository.deleteAll()
                .then(nodeRepository.deleteAll());
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.zip(nodeRepository.count(), edgeRepository.count())
                .map(tuple -> new GraphStats(tuple.getT1(), tuple.getT2(), Map.of(), Map.of(), 0.0));
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return nodeRepository.saveAll(nodes);
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return edgeRepository.saveAll(edges);
    }
}
