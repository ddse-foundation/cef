package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for InMemoryKnowledgeGraph using JGraphT.
 * 
 * @author mrmanna
 */
class InMemoryKnowledgeGraphTest {

    private InMemoryKnowledgeGraph graph;

    @BeforeEach
    void setUp() {
        graph = new InMemoryKnowledgeGraph();
    }

    // ========== Node Operations Tests ==========

    @Test
    void shouldAddAndFindNode() {
        // Given
        Node node = createNode("Product", Map.of("name", "Laptop"));

        // When
        graph.addNode(node);

        // Then
        Optional<Node> found = graph.findNode(node.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(node.getId());
        assertThat(found.get().getLabel()).isEqualTo("Product");
    }

    @Test
    void shouldFindNodesByLabel() {
        // Given
        Node node1 = createNode("Product", Map.of("name", "Laptop"));
        Node node2 = createNode("Product", Map.of("name", "Mouse"));
        Node node3 = createNode("Customer", Map.of("name", "John"));

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        // When
        List<Node> products = graph.findNodesByLabel("Product");
        List<Node> customers = graph.findNodesByLabel("Customer");

        // Then
        assertThat(products).hasSize(2);
        assertThat(customers).hasSize(1);
        assertThat(products).extracting(Node::getLabel).containsOnly("Product");
    }

    @Test
    void shouldRemoveNode() {
        // Given
        Node node = createNode("Product", Map.of("name", "Laptop"));
        graph.addNode(node);

        // When
        graph.removeNode(node.getId());

        // Then
        assertThat(graph.findNode(node.getId())).isEmpty();
        assertThat(graph.getNodeCount()).isZero();
    }

    @Test
    void shouldThrowExceptionWhenAddingNullNode() {
        assertThrows(IllegalArgumentException.class, () -> graph.addNode(null));
    }

    // ========== Edge Operations Tests ==========

    @Test
    void shouldAddAndFindEdge() {
        // Given
        Node source = createNode("Product", Map.of("name", "Laptop"));
        Node target = createNode("Category", Map.of("name", "Electronics"));
        graph.addNode(source);
        graph.addNode(target);

        Edge edge = createEdge(source.getId(), target.getId(), "BELONGS_TO", 1.0);

        // When
        graph.addEdge(edge);

        // Then
        Set<Edge> edges = graph.getEdges(source.getId());
        assertThat(edges).hasSize(1);
        assertThat(edges).extracting(Edge::getRelationType).containsOnly("BELONGS_TO");
    }

    @Test
    void shouldRemoveEdge() {
        // Given
        Node source = createNode("Product", Map.of());
        Node target = createNode("Category", Map.of());
        graph.addNode(source);
        graph.addNode(target);

        Edge edge = createEdge(source.getId(), target.getId(), "BELONGS_TO", 1.0);
        graph.addEdge(edge);

        // When
        graph.removeEdge(edge.getId());

        // Then
        Set<Edge> edges = graph.getEdges(source.getId());
        assertThat(edges).isEmpty();
    }

    @Test
    void shouldRemoveNodeAndItsEdges() {
        // Given
        Node node1 = createNode("A", Map.of());
        Node node2 = createNode("B", Map.of());
        Node node3 = createNode("C", Map.of());

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        Edge edge1 = createEdge(node1.getId(), node2.getId(), "RELATES", 1.0);
        Edge edge2 = createEdge(node2.getId(), node3.getId(), "RELATES", 1.0);

        graph.addEdge(edge1);
        graph.addEdge(edge2);

        // When
        graph.removeNode(node2.getId());

        // Then
        assertThat(graph.findNode(node2.getId())).isEmpty();
        assertThat(graph.getEdges(node1.getId())).isEmpty();
        assertThat(graph.getEdges(node3.getId())).isEmpty();
    }

    // ========== Traversal Tests ==========

    @Test
    void shouldGetParents() {
        // Given: A <- B, A <- C
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(createEdge(nodeB.getId(), nodeA.getId(), "PARENT_OF", 1.0));
        graph.addEdge(createEdge(nodeC.getId(), nodeA.getId(), "PARENT_OF", 1.0));

        // When
        List<Node> parents = graph.getParents(nodeA.getId());

        // Then
        assertThat(parents).hasSize(2);
        assertThat(parents).extracting(Node::getLabel).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void shouldGetChildren() {
        // Given: A -> B, A -> C
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "CHILD_OF", 1.0));
        graph.addEdge(createEdge(nodeA.getId(), nodeC.getId(), "CHILD_OF", 1.0));

        // When
        List<Node> children = graph.getChildren(nodeA.getId());

        // Then
        assertThat(children).hasSize(2);
        assertThat(children).extracting(Node::getLabel).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void shouldGetSiblings() {
        // Given: P -> A, P -> B, P -> C (A, B, C are siblings)
        Node parent = createNode("Parent", Map.of());
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());

        graph.addNode(parent);
        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(createEdge(parent.getId(), nodeA.getId(), "HAS", 1.0));
        graph.addEdge(createEdge(parent.getId(), nodeB.getId(), "HAS", 1.0));
        graph.addEdge(createEdge(parent.getId(), nodeC.getId(), "HAS", 1.0));

        // When
        List<Node> siblings = graph.getSiblings(nodeA.getId());

        // Then
        assertThat(siblings).hasSize(2);
        assertThat(siblings).extracting(Node::getLabel).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    void shouldGetNeighborsWithDepth() {
        // Given: A -> B -> C -> D
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());
        Node nodeD = createNode("D", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "NEXT", 1.0));
        graph.addEdge(createEdge(nodeB.getId(), nodeC.getId(), "NEXT", 1.0));
        graph.addEdge(createEdge(nodeC.getId(), nodeD.getId(), "NEXT", 1.0));

        // When
        List<Node> depth1 = graph.getNeighbors(nodeA.getId(), 1);
        List<Node> depth2 = graph.getNeighbors(nodeA.getId(), 2);
        List<Node> depth3 = graph.getNeighbors(nodeA.getId(), 3);

        // Then
        assertThat(depth1).hasSize(1).extracting(Node::getLabel).contains("B");
        assertThat(depth2).hasSize(2).extracting(Node::getLabel).containsExactlyInAnyOrder("B", "C");
        assertThat(depth3).hasSize(3).extracting(Node::getLabel).containsExactlyInAnyOrder("B", "C", "D");
    }

    // ========== Path Finding Tests ==========

    @Test
    void shouldFindShortestPath() {
        // Given: A -> B -> C
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "LINK", 1.0));
        graph.addEdge(createEdge(nodeB.getId(), nodeC.getId(), "LINK", 2.0));

        // When
        Optional<GraphPathResult> path = graph.findShortestPath(nodeA.getId(), nodeC.getId());

        // Then
        assertThat(path).isPresent();
        assertThat(path.get().nodeIds()).containsExactly(nodeA.getId(), nodeB.getId(), nodeC.getId());
        assertThat(path.get().length()).isEqualTo(2);
        assertThat(path.get().totalWeight()).isEqualTo(3.0);
        assertThat(path.get().relationTypes()).containsExactly("LINK", "LINK");
    }

    @Test
    void shouldFindAllPaths() {
        // Given: Diamond graph A -> B -> D, A -> C -> D
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());
        Node nodeD = createNode("D", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);
        graph.addNode(nodeD);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "PATH1", 1.0));
        graph.addEdge(createEdge(nodeB.getId(), nodeD.getId(), "PATH1", 1.0));
        graph.addEdge(createEdge(nodeA.getId(), nodeC.getId(), "PATH2", 1.0));
        graph.addEdge(createEdge(nodeC.getId(), nodeD.getId(), "PATH2", 1.0));

        // When
        List<GraphPathResult> paths = graph.findAllPaths(nodeA.getId(), nodeD.getId(), 3);

        // Then
        assertThat(paths).hasSize(2);
        assertThat(paths).allMatch(p -> p.length() == 2);
    }

    @Test
    void shouldReturnEmptyForNonExistentPath() {
        // Given: A -> B, C (disconnected)
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        Node nodeC = createNode("C", Map.of());

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "LINK", 1.0));

        // When
        Optional<GraphPathResult> path = graph.findShortestPath(nodeA.getId(), nodeC.getId());

        // Then
        assertThat(path).isEmpty();
    }

    // ========== Statistics Tests ==========

    @Test
    void shouldGetNodeCount() {
        // Given
        graph.addNode(createNode("A", Map.of()));
        graph.addNode(createNode("B", Map.of()));
        graph.addNode(createNode("C", Map.of()));

        // When/Then
        assertThat(graph.getNodeCount()).isEqualTo(3);
    }

    @Test
    void shouldGetEdgeCount() {
        // Given
        Node nodeA = createNode("A", Map.of());
        Node nodeB = createNode("B", Map.of());
        graph.addNode(nodeA);
        graph.addNode(nodeB);

        graph.addEdge(createEdge(nodeA.getId(), nodeB.getId(), "LINK", 1.0));
        graph.addEdge(createEdge(nodeB.getId(), nodeA.getId(), "LINK", 1.0));

        // When/Then
        assertThat(graph.getEdgeCount()).isEqualTo(2);
    }

    @Test
    void shouldGetLabelCounts() {
        // Given
        graph.addNode(createNode("Product", Map.of()));
        graph.addNode(createNode("Product", Map.of()));
        graph.addNode(createNode("Customer", Map.of()));

        // When
        Map<String, Long> counts = graph.getLabelCounts();

        // Then
        assertThat(counts).containsEntry("Product", 2L);
        assertThat(counts).containsEntry("Customer", 1L);
    }

    // ========== Sync Operations Tests ==========

    @Test
    void shouldClearGraph() {
        // Given
        Node node = createNode("A", Map.of());
        graph.addNode(node);
        graph.addEdge(createEdge(node.getId(), node.getId(), "SELF", 1.0));

        // When
        graph.clear();

        // Then
        assertThat(graph.getNodeCount()).isZero();
        assertThat(graph.getEdgeCount()).isZero();
    }

    @Test
    void shouldLoadFromDatabase() {
        // Given
        List<Node> nodes = List.of(
                createNode("A", Map.of()),
                createNode("B", Map.of()),
                createNode("C", Map.of()));

        List<Edge> edges = List.of(
                createEdge(nodes.get(0).getId(), nodes.get(1).getId(), "LINK", 1.0),
                createEdge(nodes.get(1).getId(), nodes.get(2).getId(), "LINK", 1.0));

        // When
        graph.loadFromDatabase(Flux.fromIterable(nodes), Flux.fromIterable(edges));

        // Then
        assertThat(graph.getNodeCount()).isEqualTo(3);
        assertThat(graph.getEdgeCount()).isEqualTo(2);
    }

    // ========== Concurrent Access Test ==========

    @Test
    void shouldHandleConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When - Multiple threads adding nodes concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Node node = createNode("Thread" + threadId, Map.of("index", j));
                        graph.addNode(node);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertThat(graph.getNodeCount()).isEqualTo(threadCount * operationsPerThread);
    }

    // ========== Helper Methods ==========

    private Node createNode(String label, Map<String, Object> properties) {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setLabel(label);
        node.setProperties(properties);
        return node;
    }

    private Edge createEdge(UUID sourceId, UUID targetId, String relationType, Double weight) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationType(relationType);
        edge.setWeight(weight);
        return edge;
    }
}
