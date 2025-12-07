package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationSemantics;
import org.jgrapht.Graph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DirectedWeightedPseudograph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory knowledge graph using JGraphT for fast traversal operations.
 * Provides O(1) node lookups and efficient graph algorithms.
 * 
 * <p>This class is NOT a Spring component by default. It should only be instantiated
 * by GraphStoreAutoConfiguration when cef.graph.store=in-memory is set.</p>
 * 
 * Thread-safe: Uses ConcurrentHashMap for indexes.
 * 
 * @author mrmanna
 */
public class InMemoryKnowledgeGraph {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKnowledgeGraph.class);

    // JGraphT graph: allows multiple edges, self-loops, weights
    private final Graph<UUID, EdgeWrapper> graph;

    // Indexes for fast lookups
    private final Map<UUID, Node> nodeIndex;
    private final Map<String, Set<UUID>> labelIndex;
    private final Map<UUID, Edge> edgeIndex;

    public InMemoryKnowledgeGraph() {
        this.graph = new DirectedWeightedPseudograph<>(EdgeWrapper.class);
        this.nodeIndex = new ConcurrentHashMap<>();
        this.labelIndex = new ConcurrentHashMap<>();
        this.edgeIndex = new ConcurrentHashMap<>();
        log.info("Initialized InMemoryKnowledgeGraph with JGraphT DirectedWeightedPseudograph");
    }

    // ========== Node Operations ==========

    /**
     * Add a node to the graph.
     */
    public synchronized void addNode(Node node) {
        if (node == null || node.getId() == null) {
            throw new IllegalArgumentException("Node and node ID cannot be null");
        }

        UUID nodeId = node.getId();

        // Add to graph
        graph.addVertex(nodeId);

        // Add to indexes
        nodeIndex.put(nodeId, node);

        if (node.getLabel() != null) {
            labelIndex.computeIfAbsent(node.getLabel(), k -> ConcurrentHashMap.newKeySet())
                    .add(nodeId);
        }

        // log.debug("Added node: id={}, label={}", nodeId, node.getLabel());
    }

    /**
     * Remove a node and all its connected edges.
     */
    public synchronized void removeNode(UUID nodeId) {
        if (nodeId == null) {
            return;
        }

        // Remove from graph (automatically removes connected edges)
        graph.removeVertex(nodeId);

        // Remove from indexes
        Node node = nodeIndex.remove(nodeId);
        if (node != null && node.getLabel() != null) {
            Set<UUID> labelSet = labelIndex.get(node.getLabel());
            if (labelSet != null) {
                labelSet.remove(nodeId);
                if (labelSet.isEmpty()) {
                    labelIndex.remove(node.getLabel());
                }
            }
        }

        // Remove associated edges from edge index
        edgeIndex.values()
                .removeIf(edge -> edge.getSourceNodeId().equals(nodeId) || edge.getTargetNodeId().equals(nodeId));

        log.debug("Removed node: id={}", nodeId);
    }

    /**
     * Find a node by ID. O(1) lookup.
     */
    public Optional<Node> findNode(UUID nodeId) {
        return Optional.ofNullable(nodeIndex.get(nodeId));
    }

    /**
     * Find all nodes with a given label. O(1) lookup.
     */
    public List<Node> findNodesByLabel(String label) {
        Set<UUID> nodeIds = labelIndex.getOrDefault(label, Set.of());
        return nodeIds.stream()
                .map(nodeIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ========== Edge Operations ==========

    /**
     * Add an edge to the graph.
     */
    public synchronized void addEdge(Edge edge) {
        if (edge == null || edge.getId() == null) {
            throw new IllegalArgumentException("Edge and edge ID cannot be null");
        }

        UUID sourceId = edge.getSourceNodeId();
        UUID targetId = edge.getTargetNodeId();

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Source and target node IDs cannot be null");
        }

        // Ensure vertices exist
        if (!graph.containsVertex(sourceId)) {
            graph.addVertex(sourceId);
            log.warn("Source node {} not in graph, added vertex", sourceId);
        }
        if (!graph.containsVertex(targetId)) {
            graph.addVertex(targetId);
            log.warn("Target node {} not in graph, added vertex", targetId);
        }

        // Add edge to graph
        EdgeWrapper wrapper = new EdgeWrapper(edge);
        graph.addEdge(sourceId, targetId, wrapper);
        graph.setEdgeWeight(wrapper, wrapper.getWeight());

        // Add to edge index
        edgeIndex.put(edge.getId(), edge);

        // log.debug("Added edge: id={}, type={}, source={}, target={}",
        // edge.getId(), edge.getRelationType(), sourceId, targetId);
    }

    /**
     * Remove an edge by ID.
     */
    public synchronized void removeEdge(UUID edgeId) {
        Edge edge = edgeIndex.remove(edgeId);
        if (edge != null) {
            EdgeWrapper wrapper = new EdgeWrapper(edge);
            graph.removeEdge(wrapper);
            log.debug("Removed edge: id={}", edgeId);
        }
    }

    /**
     * Get all edges connected to a node (incoming or outgoing).
     */
    public Set<Edge> getEdges(UUID nodeId) {
        if (!graph.containsVertex(nodeId)) {
            return Set.of();
        }

        Set<EdgeWrapper> wrappers = graph.edgesOf(nodeId);
        return wrappers.stream()
                .map(EdgeWrapper::getEdge)
                .collect(Collectors.toSet());
    }

    // ========== Graph Traversal ==========

    /**
     * Get parent nodes (nodes with edges pointing to this node).
     */
    public List<Node> getParents(UUID nodeId) {
        if (!graph.containsVertex(nodeId)) {
            return List.of();
        }

        Set<EdgeWrapper> incomingEdges = graph.incomingEdgesOf(nodeId);
        return incomingEdges.stream()
                .map(wrapper -> wrapper.getEdge().getSourceNodeId())
                .distinct()
                .map(nodeIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get child nodes (nodes this node points to).
     */
    public List<Node> getChildren(UUID nodeId) {
        if (!graph.containsVertex(nodeId)) {
            return List.of();
        }

        Set<EdgeWrapper> outgoingEdges = graph.outgoingEdgesOf(nodeId);
        return outgoingEdges.stream()
                .map(wrapper -> wrapper.getEdge().getTargetNodeId())
                .distinct()
                .map(nodeIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get sibling nodes (nodes sharing the same parent).
     */
    public List<Node> getSiblings(UUID nodeId) {
        List<Node> parents = getParents(nodeId);
        return parents.stream()
                .flatMap(parent -> getChildren(parent.getId()).stream())
                .filter(sibling -> !sibling.getId().equals(nodeId))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Get neighboring nodes within a certain depth using BFS.
     */
    public List<Node> getNeighbors(UUID nodeId, int depth) {
        if (!graph.containsVertex(nodeId) || depth <= 0) {
            return List.of();
        }

        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> depths = new HashMap<>();

        queue.add(nodeId);
        depths.put(nodeId, 0);
        visited.add(nodeId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depths.get(current);

            if (currentDepth >= depth) {
                continue;
            }

            // Add neighbors
            Set<EdgeWrapper> edges = graph.edgesOf(current);
            for (EdgeWrapper wrapper : edges) {
                Edge edge = wrapper.getEdge();
                UUID neighbor = edge.getSourceNodeId().equals(current) ? edge.getTargetNodeId()
                        : edge.getSourceNodeId();

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    depths.put(neighbor, currentDepth + 1);
                }
            }
        }

        // Remove the starting node and return
        visited.remove(nodeId);
        return visited.stream()
                .map(nodeIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get neighbors with filtering by relation type and direction.
     */
    public List<Node> getNeighbors(UUID nodeId, String relationType, Direction direction) {
        if (!graph.containsVertex(nodeId)) {
            return List.of();
        }

        Set<EdgeWrapper> edges;
        if (direction == Direction.INCOMING) {
            edges = graph.incomingEdgesOf(nodeId);
        } else if (direction == Direction.OUTGOING) {
            edges = graph.outgoingEdgesOf(nodeId);
        } else {
            edges = graph.edgesOf(nodeId);
        }

        return edges.stream()
                .map(EdgeWrapper::getEdge)
                .filter(edge -> relationType == null || edge.getRelationType().equals(relationType))
                .map(edge -> {
                    if (direction == Direction.INCOMING)
                        return edge.getSourceNodeId();
                    if (direction == Direction.OUTGOING)
                        return edge.getTargetNodeId();
                    return edge.getSourceNodeId().equals(nodeId) ? edge.getTargetNodeId() : edge.getSourceNodeId();
                })
                .distinct()
                .map(nodeIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ========== Path Finding ==========

    /**
     * Find shortest path between two nodes using Dijkstra's algorithm.
     */
    public Optional<GraphPathResult> findShortestPath(UUID fromId, UUID toId) {
        if (!graph.containsVertex(fromId) || !graph.containsVertex(toId)) {
            return Optional.empty();
        }

        DijkstraShortestPath<UUID, EdgeWrapper> dijkstra = new DijkstraShortestPath<>(graph);

        org.jgrapht.GraphPath<UUID, EdgeWrapper> path = dijkstra.getPath(fromId, toId);

        return Optional.ofNullable(GraphPathResult.fromJGraphTPath(path));
    }

    /**
     * Find all paths between two nodes up to a maximum depth (BFS-based).
     */
    public List<GraphPathResult> findAllPaths(UUID fromId, UUID toId, int maxDepth) {
        if (!graph.containsVertex(fromId) || !graph.containsVertex(toId) || maxDepth <= 0) {
            return List.of();
        }

        List<GraphPathResult> results = new ArrayList<>();
        List<UUID> currentPath = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        findPathsRecursive(fromId, toId, currentPath, visited, results, maxDepth, 0);

        return results;
    }

    private void findPathsRecursive(UUID current, UUID target, List<UUID> path,
            Set<UUID> visited, List<GraphPathResult> results,
            int maxDepth, int currentDepth) {
        if (currentDepth > maxDepth) {
            return;
        }

        path.add(current);
        visited.add(current);

        if (current.equals(target)) {
            // Found a path
            List<String> relationTypes = new ArrayList<>();
            double totalWeight = 0.0;

            for (int i = 0; i < path.size() - 1; i++) {
                UUID from = path.get(i);
                UUID to = path.get(i + 1);

                // Find edge between these nodes
                Set<EdgeWrapper> edges = graph.getAllEdges(from, to);
                if (!edges.isEmpty()) {
                    EdgeWrapper edge = edges.iterator().next();
                    relationTypes.add(edge.getEdge().getRelationType());
                    totalWeight += edge.getWeight();
                }
            }

            results.add(new GraphPathResult(
                    new ArrayList<>(path),
                    relationTypes,
                    totalWeight,
                    path.size() - 1));
        } else {
            // Continue searching
            Set<EdgeWrapper> outgoingEdges = graph.outgoingEdgesOf(current);
            for (EdgeWrapper wrapper : outgoingEdges) {
                UUID next = wrapper.getEdge().getTargetNodeId();
                if (!visited.contains(next)) {
                    findPathsRecursive(next, target, path, visited, results, maxDepth, currentDepth + 1);
                }
            }
        }

        // Backtrack
        path.remove(path.size() - 1);
        visited.remove(current);
    }

    /**
     * BFS traversal for reasoning context extraction with semantic filtering.
     */
    public Set<Node> traverse(UUID startId, int depth, Set<RelationSemantics> semantics) {
        if (!graph.containsVertex(startId) || depth <= 0) {
            return Set.of();
        }

        Set<Node> result = new HashSet<>();
        BreadthFirstIterator<UUID, EdgeWrapper> iterator = new BreadthFirstIterator<>(graph, startId);

        Map<UUID, Integer> depths = new HashMap<>();
        depths.put(startId, 0);

        while (iterator.hasNext()) {
            UUID nodeId = iterator.next();
            int nodeDepth = depths.getOrDefault(nodeId, 0);

            if (nodeDepth > depth) {
                continue;
            }

            Node node = nodeIndex.get(nodeId);
            if (node != null) {
                result.add(node);
            }

            // Add children with depth tracking
            Set<EdgeWrapper> outgoing = graph.outgoingEdgesOf(nodeId);
            for (EdgeWrapper wrapper : outgoing) {
                UUID childId = wrapper.getEdge().getTargetNodeId();
                depths.putIfAbsent(childId, nodeDepth + 1);
            }
        }

        return result;
    }

    // ========== Statistics ==========

    /**
     * Get total number of nodes in the graph.
     */
    public long getNodeCount() {
        return nodeIndex.size();
    }

    /**
     * Get total number of edges in the graph.
     */
    public long getEdgeCount() {
        return edgeIndex.size();
    }

    /**
     * Get count of nodes by label.
     */
    public Map<String, Long> getLabelCounts() {
        return labelIndex.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (long) e.getValue().size()));
    }

    // ========== Sync Operations ==========

    /**
     * Clear all nodes and edges from the graph.
     */
    public synchronized void clear() {
        graph.removeAllVertices(new HashSet<>(graph.vertexSet()));
        nodeIndex.clear();
        labelIndex.clear();
        edgeIndex.clear();
        log.info("Cleared InMemoryKnowledgeGraph");
    }

    /**
     * Load graph from database (bulk operation).
     */
    public void loadFromDatabase(Flux<Node> nodes, Flux<Edge> edges) {
        log.info("Loading graph from database...");
        long start = System.currentTimeMillis();

        // Load nodes
        long nodeCount = nodes.doOnNext(this::addNode)
                .count()
                .block();

        // Load edges
        long edgeCount = edges.doOnNext(this::addEdge)
                .count()
                .block();

        long duration = System.currentTimeMillis() - start;
        log.info("Loaded {} nodes and {} edges in {}ms", nodeCount, edgeCount, duration);
    }
}
