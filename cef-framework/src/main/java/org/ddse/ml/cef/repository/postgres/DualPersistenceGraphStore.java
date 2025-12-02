package org.ddse.ml.cef.repository.postgres;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.graph.InMemoryKnowledgeGraph;
import org.ddse.ml.cef.graph.GraphStats;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * Dual persistence implementation of GraphStore.
 * Coordinates between database persistence (R2DBC) and in-memory graph
 * (JGraphT).
 * 
 * Every write operation updates BOTH:
 * - Database (for durability)
 * - In-memory graph (for fast O(1) retrieval)
 * 
 * On startup, loads entire graph from database into memory.
 * 
 * @author mrmanna
 */
@Component
@Primary
@ConditionalOnProperty(name = "cef.database.type", havingValue = "postgresql")
public class DualPersistenceGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(DualPersistenceGraphStore.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final InMemoryKnowledgeGraph inMemoryGraph;

    public DualPersistenceGraphStore(NodeRepository nodeRepository,
            EdgeRepository edgeRepository,
            InMemoryKnowledgeGraph inMemoryGraph) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.inMemoryGraph = inMemoryGraph;
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        log.info("Initializing dual persistence graph store");
        return Mono.empty();
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return nodeRepository.save(node)
                .doOnNext(savedNode -> {
                    // Update in-memory graph synchronously
                    inMemoryGraph.addNode(savedNode);
                    log.debug("Node {} added to both DB and in-memory graph", savedNode.getId());
                });
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return nodeRepository.saveAll(nodes)
                .doOnNext(savedNode -> {
                    // Update in-memory graph for each saved node
                    inMemoryGraph.addNode(savedNode);
                })
                .doOnComplete(() -> log.info("Batch added {} nodes to both DB and in-memory graph", nodes.size()));
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return edgeRepository.save(edge)
                .doOnNext(savedEdge -> {
                    // Update in-memory graph synchronously
                    inMemoryGraph.addEdge(savedEdge);
                    log.debug("Edge {} added to both DB and in-memory graph", savedEdge.getId());
                });
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return edgeRepository.saveAll(edges)
                .doOnNext(savedEdge -> {
                    // Update in-memory graph for each saved edge
                    inMemoryGraph.addEdge(savedEdge);
                })
                .doOnComplete(() -> log.info("Batch added {} edges to both DB and in-memory graph", edges.size()));
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        // Read from in-memory graph (O(1))
        return Mono.fromCallable(() -> inMemoryGraph.findNode(nodeId).orElse(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        // Read from database (in-memory graph doesn't index edges by ID)
        return edgeRepository.findById(edgeId);
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        // Read from in-memory graph (O(1))
        return Flux.fromIterable(inMemoryGraph.findNodesByLabel(label))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        // Read from database
        return edgeRepository.findByRelationType(relationType);
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        // Read from in-memory graph (fast BFS)
        return Flux.fromIterable(inMemoryGraph.getNeighbors(nodeId, 1))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        // Need to filter by relation type - query database
        return edgeRepository.findByNodeId(nodeId)
                .filter(edge -> edge.getRelationType().equals(relationType))
                .flatMap(edge -> {
                    UUID neighborId = edge.getSourceNodeId().equals(nodeId)
                            ? edge.getTargetNodeId()
                            : edge.getSourceNodeId();
                    return getNode(neighborId);
                })
                .distinct();
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.fromCallable(() -> {
            var pathResult = inMemoryGraph.findShortestPath(sourceId, targetId);
            return pathResult.map(result -> result.nodeIds()).orElse(List.of());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        // Use in-memory graph for fast BFS traversal
        return Flux.fromIterable(inMemoryGraph.getNeighbors(nodeId, depth))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        return Mono.fromCallable(() -> {
            // Use in-memory graph for subgraph extraction
            java.util.Set<Node> nodes = new java.util.HashSet<>();
            java.util.Set<Edge> edges = new java.util.HashSet<>();

            for (UUID seedId : seedNodeIds) {
                inMemoryGraph.findNode(seedId).ifPresent(nodes::add);
                nodes.addAll(inMemoryGraph.getNeighbors(seedId, depth));

                for (Node node : nodes) {
                    edges.addAll(inMemoryGraph.getEdges(node.getId()));
                }
            }

            return new GraphSubgraph(List.copyOf(nodes), List.copyOf(edges));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return nodeRepository.deleteById(nodeId)
                .doOnSuccess(v -> {
                    inMemoryGraph.removeNode(nodeId);
                    log.debug("Node {} deleted from both DB and in-memory graph", nodeId);
                });
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return edgeRepository.deleteById(edgeId)
                .doOnSuccess(v -> {
                    inMemoryGraph.removeEdge(edgeId);
                    log.debug("Edge {} deleted from both DB and in-memory graph", edgeId);
                });
    }

    @Override
    public Mono<Void> clear() {
        return edgeRepository.deleteAll()
                .then(nodeRepository.deleteAll())
                .doOnSuccess(v -> {
                    inMemoryGraph.clear();
                    log.info("Cleared both DB and in-memory graph");
                });
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.fromCallable(() -> {
            long nodeCount = inMemoryGraph.getNodeCount();
            long edgeCount = inMemoryGraph.getEdgeCount();
            var labelCounts = inMemoryGraph.getLabelCounts();

            // Calculate average degree
            double averageDegree = nodeCount > 0 ? (double) edgeCount / nodeCount : 0.0;

            return new GraphStats(nodeCount, edgeCount, labelCounts,
                    java.util.Map.of(), averageDegree);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Load entire graph from database into memory.
     * Called on application startup via @PostConstruct or ApplicationListener.
     */
    public Mono<Void> loadFromDatabase() {
        log.info("Loading graph from database into memory...");
        long start = System.currentTimeMillis();

        return Mono.zip(
                nodeRepository.findAll().collectList(),
                edgeRepository.findAll().collectList())
                .doOnNext(tuple -> {
                    List<Node> nodes = tuple.getT1();
                    List<Edge> edges = tuple.getT2();

                    // Load nodes into in-memory graph
                    nodes.forEach(inMemoryGraph::addNode);

                    // Load edges into in-memory graph
                    edges.forEach(inMemoryGraph::addEdge);

                    long duration = System.currentTimeMillis() - start;
                    log.info("Loaded {} nodes and {} edges into memory in {}ms",
                            nodes.size(), edges.size(), duration);
                })
                .then();
    }
}
