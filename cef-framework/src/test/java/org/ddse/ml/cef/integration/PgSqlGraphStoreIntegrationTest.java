package org.ddse.ml.cef.integration;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationSemantics;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.graph.GraphStats;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.ddse.ml.cef.graph.PgSqlGraphStore;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for PgSqlGraphStore using real PostgreSQL via Testcontainers.
 * NO MOCKS - all tests run against a real PostgreSQL database.
 * 
 * @author mrmanna
 * @since v0.6
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PgSqlGraphStoreIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cef_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupTimeout(Duration.ofMinutes(2));

    private static DataSource dataSource;
    private static PgSqlGraphStore graphStore;

    private static final List<RelationType> RELATION_TYPES = List.of(
            new RelationType("TREATS", "Doctor", "Patient", RelationSemantics.CAUSAL, true),
            new RelationType("HAS_CONDITION", "Patient", "Condition", RelationSemantics.ASSOCIATIVE, true),
            new RelationType("PRESCRIBED", "Doctor", "Medication", RelationSemantics.CAUSAL, true),
            new RelationType("RELATED_TO", "Node", "Node", RelationSemantics.ASSOCIATIVE, false)
    );

    @BeforeAll
    static void setupAll() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        
        dataSource = new HikariDataSource(config);
        graphStore = new PgSqlGraphStore(dataSource, 5);
        graphStore.initialize(RELATION_TYPES).block();
    }

    @BeforeEach
    void setup() {
        graphStore.clear().block();
    }

    @AfterAll
    static void tearDown() {
        if (dataSource instanceof HikariDataSource) {
            ((HikariDataSource) dataSource).close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should add and retrieve a node")
    void shouldAddAndRetrieveNode() {
        Node node = createNode("Patient", "John Doe patient record", Map.of("age", 45));

        Node savedNode = graphStore.addNode(node).block();
        Node retrievedNode = graphStore.getNode(node.getId()).block();

        assertThat(savedNode).isNotNull();
        assertThat(retrievedNode).isNotNull();
        assertThat(retrievedNode.getId()).isEqualTo(node.getId());
        assertThat(retrievedNode.getLabel()).isEqualTo("Patient");
        assertThat(retrievedNode.getVectorizableContent()).isEqualTo("John Doe patient record");
        assertThat(retrievedNode.getProperties()).containsEntry("age", 45);
    }

    @Test
    @Order(2)
    @DisplayName("Should update existing node")
    void shouldUpdateExistingNode() {
        Node node = createNode("Patient", "Original content", Map.of("version", 1));
        graphStore.addNode(node).block();

        node.setVectorizableContent("Updated content");
        node.setProperties(Map.of("version", 2, "updated", true));
        graphStore.addNode(node).block();

        Node retrieved = graphStore.getNode(node.getId()).block();

        assertThat(retrieved.getVectorizableContent()).isEqualTo("Updated content");
        assertThat(retrieved.getProperties()).containsEntry("version", 2);
        assertThat(retrieved.getProperties()).containsEntry("updated", true);
    }

    @Test
    @Order(3)
    @DisplayName("Should add and retrieve an edge")
    void shouldAddAndRetrieveEdge() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient = createNode("Patient", "John Doe");
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient).block();

        Edge edge = createEdge(doctor.getId(), patient.getId(), "TREATS", 0.95, Map.of("since", "2023"));

        Edge savedEdge = graphStore.addEdge(edge).block();
        Edge retrievedEdge = graphStore.getEdge(edge.getId()).block();

        assertThat(savedEdge).isNotNull();
        assertThat(retrievedEdge).isNotNull();
        assertThat(retrievedEdge.getSourceNodeId()).isEqualTo(doctor.getId());
        assertThat(retrievedEdge.getTargetNodeId()).isEqualTo(patient.getId());
        assertThat(retrievedEdge.getRelationType()).isEqualTo("TREATS");
        assertThat(retrievedEdge.getWeight()).isEqualTo(0.95);
    }

    @Test
    @Order(4)
    @DisplayName("Should find nodes by label")
    void shouldFindNodesByLabel() {
        graphStore.addNode(createNode("Patient", "Patient 1")).block();
        graphStore.addNode(createNode("Patient", "Patient 2")).block();
        graphStore.addNode(createNode("Doctor", "Doctor 1")).block();

        List<Node> patients = graphStore.findNodesByLabel("Patient").collectList().block();
        List<Node> doctors = graphStore.findNodesByLabel("Doctor").collectList().block();

        assertThat(patients).hasSize(2);
        assertThat(doctors).hasSize(1);
    }

    @Test
    @Order(5)
    @DisplayName("Should find edges by relation type")
    void shouldFindEdgesByRelationType() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient1 = createNode("Patient", "Patient 1");
        Node patient2 = createNode("Patient", "Patient 2");
        Node medication = createNode("Medication", "Aspirin");
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient1).block();
        graphStore.addNode(patient2).block();
        graphStore.addNode(medication).block();

        graphStore.addEdge(createEdge(doctor.getId(), patient1.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(doctor.getId(), patient2.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(doctor.getId(), medication.getId(), "PRESCRIBED")).block();

        List<Edge> treatsEdges = graphStore.findEdgesByRelationType("TREATS").collectList().block();
        List<Edge> prescribedEdges = graphStore.findEdgesByRelationType("PRESCRIBED").collectList().block();

        assertThat(treatsEdges).hasSize(2);
        assertThat(prescribedEdges).hasSize(1);
    }

    @Test
    @Order(6)
    @DisplayName("Should delete node and cascade edges")
    void shouldDeleteNodeAndCascadeEdges() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient = createNode("Patient", "John Doe");
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient).block();
        
        Edge edge = createEdge(doctor.getId(), patient.getId(), "TREATS");
        graphStore.addEdge(edge).block();

        graphStore.deleteNode(doctor.getId()).block();

        assertThat(graphStore.getNode(doctor.getId()).block()).isNull();
        assertThat(graphStore.getEdge(edge.getId()).block()).isNull();
        assertThat(graphStore.getNode(patient.getId()).block()).isNotNull();
    }

    @Test
    @Order(7)
    @DisplayName("Should get direct neighbors")
    void shouldGetDirectNeighbors() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient1 = createNode("Patient", "Patient 1");
        Node patient2 = createNode("Patient", "Patient 2");
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient1).block();
        graphStore.addNode(patient2).block();

        graphStore.addEdge(createEdge(doctor.getId(), patient1.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(doctor.getId(), patient2.getId(), "TREATS")).block();

        List<Node> neighbors = graphStore.getNeighbors(doctor.getId()).collectList().block();

        assertThat(neighbors).hasSize(2);
        assertThat(neighbors).extracting(Node::getLabel).containsOnly("Patient");
    }

    @Test
    @Order(8)
    @DisplayName("Should get neighbors by relation type")
    void shouldGetNeighborsByRelationType() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient = createNode("Patient", "John Doe");
        Node medication = createNode("Medication", "Aspirin");
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient).block();
        graphStore.addNode(medication).block();

        graphStore.addEdge(createEdge(doctor.getId(), patient.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(doctor.getId(), medication.getId(), "PRESCRIBED")).block();

        List<Node> treatedPatients = graphStore.getNeighborsByRelationType(doctor.getId(), "TREATS")
                .collectList().block();

        assertThat(treatedPatients).hasSize(1);
        assertThat(treatedPatients.get(0).getLabel()).isEqualTo("Patient");
    }

    @Test
    @Order(9)
    @DisplayName("Should find k-hop neighbors")
    void shouldFindKHopNeighbors() {
        Node a = createNode("Node", "A");
        Node b = createNode("Node", "B");
        Node c = createNode("Node", "C");
        Node d = createNode("Node", "D");
        
        graphStore.addNode(a).block();
        graphStore.addNode(b).block();
        graphStore.addNode(c).block();
        graphStore.addNode(d).block();

        graphStore.addEdge(createEdge(a.getId(), b.getId(), "RELATED_TO")).block();
        graphStore.addEdge(createEdge(b.getId(), c.getId(), "RELATED_TO")).block();
        graphStore.addEdge(createEdge(c.getId(), d.getId(), "RELATED_TO")).block();

        List<Node> oneHop = graphStore.findKHopNeighbors(a.getId(), 1).collectList().block();
        List<Node> twoHop = graphStore.findKHopNeighbors(a.getId(), 2).collectList().block();
        List<Node> threeHop = graphStore.findKHopNeighbors(a.getId(), 3).collectList().block();

        assertThat(oneHop).hasSize(1);
        assertThat(twoHop).hasSize(2);
        assertThat(threeHop).hasSize(3);
    }

    @Test
    @Order(10)
    @DisplayName("Should find shortest path")
    void shouldFindShortestPath() {
        Node a = createNode("Node", "A");
        Node b = createNode("Node", "B");
        Node c = createNode("Node", "C");
        Node d = createNode("Node", "D");
        
        graphStore.addNode(a).block();
        graphStore.addNode(b).block();
        graphStore.addNode(c).block();
        graphStore.addNode(d).block();

        graphStore.addEdge(createEdge(a.getId(), b.getId(), "RELATED_TO")).block();
        graphStore.addEdge(createEdge(b.getId(), c.getId(), "RELATED_TO")).block();
        graphStore.addEdge(createEdge(a.getId(), d.getId(), "RELATED_TO")).block();
        graphStore.addEdge(createEdge(d.getId(), c.getId(), "RELATED_TO")).block();

        List<UUID> path = graphStore.findShortestPath(a.getId(), c.getId()).block();

        assertThat(path).isNotEmpty();
        assertThat(path.size()).isEqualTo(3);
        assertThat(path.get(0)).isEqualTo(a.getId());
        assertThat(path.get(path.size() - 1)).isEqualTo(c.getId());
    }

    @Test
    @Order(11)
    @DisplayName("Should extract subgraph around seed nodes")
    void shouldExtractSubgraph() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient = createNode("Patient", "John Doe");
        Node disease = createNode("Disease", "Diabetes");
        Node medication = createNode("Medication", "Metformin");
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient).block();
        graphStore.addNode(disease).block();
        graphStore.addNode(medication).block();

        graphStore.addEdge(createEdge(doctor.getId(), patient.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(patient.getId(), disease.getId(), "HAS_CONDITION")).block();
        graphStore.addEdge(createEdge(doctor.getId(), medication.getId(), "PRESCRIBED")).block();

        GraphSubgraph subgraph = graphStore.extractSubgraph(List.of(patient.getId()), 1).block();

        assertThat(subgraph.getNodes()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(subgraph.getEdges()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(12)
    @DisplayName("Should batch add nodes efficiently")
    void shouldBatchAddNodes() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            nodes.add(createNode("TestNode", "Node " + i, Map.of("index", i)));
        }

        long startTime = System.currentTimeMillis();
        List<Node> savedNodes = graphStore.batchAddNodes(nodes).collectList().block();
        long duration = System.currentTimeMillis() - startTime;

        assertThat(savedNodes).hasSize(100);
        System.out.println("Batch inserted 100 nodes in " + duration + "ms");
        
        GraphStats stats = graphStore.getStatistics().block();
        assertThat(stats.getNodeCount()).isEqualTo(100);
    }

    @Test
    @Order(13)
    @DisplayName("Should batch add edges efficiently")
    void shouldBatchAddEdges() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            nodes.add(createNode("Node", "Node " + i));
        }
        graphStore.batchAddNodes(nodes).collectList().block();

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            edges.add(createEdge(nodes.get(i).getId(), nodes.get(i + 1).getId(), "TREATS"));
        }

        List<Edge> savedEdges = graphStore.batchAddEdges(edges).collectList().block();

        assertThat(savedEdges).hasSize(19);
        
        GraphStats stats = graphStore.getStatistics().block();
        assertThat(stats.getEdgeCount()).isEqualTo(19);
    }

    @Test
    @Order(14)
    @DisplayName("Should compute graph statistics")
    void shouldComputeStatistics() {
        Node doctor = createNode("Doctor", "Dr. Smith");
        Node patient1 = createNode("Patient", "Patient 1");
        Node patient2 = createNode("Patient", "Patient 2");
        Node disease = createNode("Disease", "Flu");
        Node medication = createNode("Medication", "Tamiflu");
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient1).block();
        graphStore.addNode(patient2).block();
        graphStore.addNode(disease).block();
        graphStore.addNode(medication).block();

        graphStore.addEdge(createEdge(doctor.getId(), patient1.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(doctor.getId(), patient2.getId(), "TREATS")).block();
        graphStore.addEdge(createEdge(patient1.getId(), disease.getId(), "HAS_CONDITION")).block();
        graphStore.addEdge(createEdge(doctor.getId(), medication.getId(), "PRESCRIBED")).block();

        GraphStats stats = graphStore.getStatistics().block();

        assertThat(stats.getNodeCount()).isEqualTo(5);
        assertThat(stats.getEdgeCount()).isEqualTo(4);
        assertThat(stats.getAverageDegree()).isGreaterThan(0);
        assertThat(stats.getNodeCountByLabel()).containsKey("Doctor");
        assertThat(stats.getNodeCountByLabel()).containsKey("Patient");
        assertThat(stats.getNodeCountByLabel().get("Patient")).isEqualTo(2L);
        assertThat(stats.getEdgeCountByType()).containsKey("TREATS");
        assertThat(stats.getEdgeCountByType().get("TREATS")).isEqualTo(2L);
    }

    @Test
    @Order(15)
    @DisplayName("Should handle concurrent reads and writes")
    void shouldHandleConcurrentAccess() throws Exception {
        int numThreads = 10;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        Node node = createNode("ConcurrentNode", "Thread " + threadId + " Node " + i);
                        graphStore.addNode(node).block();
                        
                        Node retrieved = graphStore.getNode(node.getId()).block();
                        if (retrieved != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(numThreads * operationsPerThread);
    }

    @Test
    @Order(16)
    @DisplayName("Should handle empty graph operations gracefully")
    void shouldHandleEmptyGraph() {
        assertThat(graphStore.getNode(UUID.randomUUID()).block()).isNull();
        assertThat(graphStore.getNeighbors(UUID.randomUUID()).collectList().block()).isEmpty();
        assertThat(graphStore.findNodesByLabel("NonExistent").collectList().block()).isEmpty();
        assertThat(graphStore.findShortestPath(UUID.randomUUID(), UUID.randomUUID()).block()).isEmpty();
        
        GraphStats stats = graphStore.getStatistics().block();
        assertThat(stats.getNodeCount()).isEqualTo(0);
        assertThat(stats.getEdgeCount()).isEqualTo(0);
    }

    @Test
    @Order(17)
    @DisplayName("Should handle null and empty properties")
    void shouldHandleNullAndEmptyProperties() {
        Node nodeWithNull = createNode("Test", "Content", null);
        Node nodeWithEmpty = createNode("Test", "Content", Map.of());
        Node nodeWithContent = createNode("Test", null, Map.of("key", "value"));

        graphStore.addNode(nodeWithNull).block();
        graphStore.addNode(nodeWithEmpty).block();
        graphStore.addNode(nodeWithContent).block();

        Node r1 = graphStore.getNode(nodeWithNull.getId()).block();
        Node r2 = graphStore.getNode(nodeWithEmpty.getId()).block();
        Node r3 = graphStore.getNode(nodeWithContent.getId()).block();

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        assertThat(r3).isNotNull();
        assertThat(r1.getProperties()).isNotNull();
        assertThat(r2.getProperties()).isNotNull();
    }

    @Test
    @Order(18)
    @DisplayName("Should clear all graph data")
    void shouldClearGraph() {
        graphStore.addNode(createNode("Test", "Node 1")).block();
        graphStore.addNode(createNode("Test", "Node 2")).block();
        
        GraphStats beforeClear = graphStore.getStatistics().block();
        assertThat(beforeClear.getNodeCount()).isEqualTo(2);

        graphStore.clear().block();

        GraphStats afterClear = graphStore.getStatistics().block();
        assertThat(afterClear.getNodeCount()).isEqualTo(0);
        assertThat(afterClear.getEdgeCount()).isEqualTo(0);
    }

    private Node createNode(String label, String content) {
        return createNode(label, content, new HashMap<>());
    }

    private Node createNode(String label, String content, Map<String, Object> properties) {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setLabel(label);
        node.setVectorizableContent(content);
        node.setProperties(properties != null ? new HashMap<>(properties) : new HashMap<>());
        node.setCreated(Instant.now());
        return node;
    }

    private Edge createEdge(UUID sourceId, UUID targetId, String relationType) {
        return createEdge(sourceId, targetId, relationType, 1.0, new HashMap<>());
    }

    private Edge createEdge(UUID sourceId, UUID targetId, String relationType, double weight, Map<String, Object> properties) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationType(relationType);
        edge.setWeight(weight);
        edge.setProperties(properties != null ? new HashMap<>(properties) : new HashMap<>());
        edge.setCreated(Instant.now());
        return edge;
    }
}
