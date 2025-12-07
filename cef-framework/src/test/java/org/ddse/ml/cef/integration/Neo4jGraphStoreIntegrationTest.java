package org.ddse.ml.cef.integration;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationSemantics;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.graph.GraphStats;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.ddse.ml.cef.graph.Neo4jGraphStore;
import org.junit.jupiter.api.*;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Neo4jGraphStore using Testcontainers.
 * 
 * <p>Tests the full Neo4j implementation with a real Neo4j Community container.
 * Validates graph operations, concurrent access, and production-scale behavior.
 * 
 * <p>Requires Docker to be running.
 * 
 * @author mrmanna
 * @since v0.6
 */
@Testcontainers
@DisplayName("Neo4j Graph Store Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Neo4jGraphStoreIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5-community")
            .withAdminPassword("testpassword")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static Driver driver;
    private static Neo4jGraphStore graphStore;
    
    // Test data IDs
    private static final UUID PATIENT_1 = UUID.randomUUID();
    private static final UUID PATIENT_2 = UUID.randomUUID();
    private static final UUID DOCTOR = UUID.randomUUID();
    private static final UUID DISEASE = UUID.randomUUID();
    private static final UUID MEDICATION = UUID.randomUUID();

    @BeforeAll
    static void setUpOnce() {
        driver = GraphDatabase.driver(
                neo4j.getBoltUrl(),
                AuthTokens.basic("neo4j", "testpassword")
        );
        graphStore = new Neo4jGraphStore(driver);
        
        // Initialize with relation types
        List<RelationType> relationTypes = List.of(
                new RelationType("TREATS", "Doctor", "Patient", RelationSemantics.CAUSAL, true),
                new RelationType("HAS_CONDITION", "Patient", "Disease", RelationSemantics.ASSOCIATIVE, true),
                new RelationType("PRESCRIBED", "Patient", "Medication", RelationSemantics.CAUSAL, true),
                new RelationType("WORKS_WITH", "Doctor", "Doctor", RelationSemantics.ASSOCIATIVE, false)
        );
        
        graphStore.initialize(relationTypes).block();
    }

    @AfterAll
    static void tearDownOnce() {
        if (driver != null) {
            driver.close();
        }
    }

    @BeforeEach
    void setUp() {
        // Clear data before each test
        graphStore.clear().block();
    }

    // ==================== Basic CRUD Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should add and retrieve a node")
    void shouldAddAndRetrieveNode() {
        Node patient = new Node(PATIENT_1, "Patient", Map.of(
                "name", "John Doe",
                "age", 45,
                "bloodType", "O+"
        ), "Patient John Doe, 45 years old, blood type O+");

        StepVerifier.create(graphStore.addNode(patient))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isEqualTo(PATIENT_1);
                    assertThat(saved.getLabel()).isEqualTo("Patient");
                })
                .verifyComplete();

        StepVerifier.create(graphStore.getNode(PATIENT_1))
                .assertNext(retrieved -> {
                    assertThat(retrieved.getId()).isEqualTo(PATIENT_1);
                    assertThat(retrieved.getLabel()).isEqualTo("Patient");
                    assertThat(retrieved.getProperties().get("name")).isEqualTo("John Doe");
                })
                .verifyComplete();
    }

    @Test
    @Order(2)
    @DisplayName("Should add and retrieve an edge")
    void shouldAddAndRetrieveEdge() {
        // Create nodes first
        Node doctor = new Node(DOCTOR, "Doctor", Map.of("name", "Dr. Smith"), null);
        Node patient = new Node(PATIENT_1, "Patient", Map.of("name", "John Doe"), null);
        
        graphStore.addNode(doctor).block();
        graphStore.addNode(patient).block();

        Edge edge = new Edge(UUID.randomUUID(), "TREATS", DOCTOR, PATIENT_1, 
                Map.of("since", "2024-01-01"), 1.0);

        StepVerifier.create(graphStore.addEdge(edge))
                .assertNext(saved -> {
                    assertThat(saved.getRelationType()).isEqualTo("TREATS");
                    assertThat(saved.getSourceNodeId()).isEqualTo(DOCTOR);
                    assertThat(saved.getTargetNodeId()).isEqualTo(PATIENT_1);
                })
                .verifyComplete();

        StepVerifier.create(graphStore.getEdge(edge.getId()))
                .assertNext(retrieved -> {
                    assertThat(retrieved.getRelationType()).isEqualTo("TREATS");
                    assertThat(retrieved.getSourceNodeId()).isEqualTo(DOCTOR);
                    assertThat(retrieved.getTargetNodeId()).isEqualTo(PATIENT_1);
                })
                .verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("Should find nodes by label")
    void shouldFindNodesByLabel() {
        // Add multiple patients
        graphStore.addNode(new Node(PATIENT_1, "Patient", Map.of("name", "John"), null)).block();
        graphStore.addNode(new Node(PATIENT_2, "Patient", Map.of("name", "Jane"), null)).block();
        graphStore.addNode(new Node(DOCTOR, "Doctor", Map.of("name", "Dr. Smith"), null)).block();

        StepVerifier.create(graphStore.findNodesByLabel("Patient").collectList())
                .assertNext(patients -> {
                    assertThat(patients).hasSize(2);
                    assertThat(patients).extracting(Node::getLabel).containsOnly("Patient");
                })
                .verifyComplete();
    }

    @Test
    @Order(4)
    @DisplayName("Should find edges by relation type")
    void shouldFindEdgesByRelationType() {
        // Setup graph
        setupMedicalGraph();

        StepVerifier.create(graphStore.findEdgesByRelationType("TREATS").collectList())
                .assertNext(edges -> {
                    assertThat(edges).isNotEmpty();
                    assertThat(edges).extracting(Edge::getRelationType).containsOnly("TREATS");
                })
                .verifyComplete();
    }

    // ==================== Graph Traversal Tests ====================

    @Test
    @Order(5)
    @DisplayName("Should get direct neighbors of a node")
    void shouldGetNeighbors() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.getNeighbors(PATIENT_1).collectList())
                .assertNext(neighbors -> {
                    assertThat(neighbors).isNotEmpty();
                    // Patient should be connected to Doctor and Disease
                })
                .verifyComplete();
    }

    @Test
    @Order(6)
    @DisplayName("Should get neighbors by relation type")
    void shouldGetNeighborsByRelationType() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.getNeighborsByRelationType(DOCTOR, "TREATS").collectList())
                .assertNext(patients -> {
                    assertThat(patients).isNotEmpty();
                    assertThat(patients).extracting(Node::getLabel).containsOnly("Patient");
                })
                .verifyComplete();
    }

    @Test
    @Order(7)
    @DisplayName("Should find k-hop neighbors")
    void shouldFindKHopNeighbors() {
        setupMedicalGraph();

        // From Doctor, 2 hops should reach Disease through Patient
        StepVerifier.create(graphStore.findKHopNeighbors(DOCTOR, 2).collectList())
                .assertNext(nodes -> {
                    assertThat(nodes).isNotEmpty();
                    // Should include Patient (1 hop) and Disease (2 hops)
                })
                .verifyComplete();
    }

    @Test
    @Order(8)
    @DisplayName("Should find shortest path between nodes")
    void shouldFindShortestPath() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.findShortestPath(DOCTOR, DISEASE))
                .assertNext(path -> {
                    assertThat(path).isNotEmpty();
                    assertThat(path.get(0)).isEqualTo(DOCTOR);
                    assertThat(path.get(path.size() - 1)).isEqualTo(DISEASE);
                })
                .verifyComplete();
    }

    @Test
    @Order(9)
    @DisplayName("Should extract subgraph around seed nodes")
    void shouldExtractSubgraph() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.extractSubgraph(List.of(PATIENT_1), 1))
                .assertNext(subgraph -> {
                    assertThat(subgraph).isNotNull();
                    assertThat(subgraph.getNodes()).isNotEmpty();
                })
                .verifyComplete();
    }

    // ==================== Delete Tests ====================

    @Test
    @Order(10)
    @DisplayName("Should delete node and connected edges")
    void shouldDeleteNode() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.deleteNode(PATIENT_1))
                .verifyComplete();

        StepVerifier.create(graphStore.getNode(PATIENT_1))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @Order(11)
    @DisplayName("Should delete edge")
    void shouldDeleteEdge() {
        setupMedicalGraph();

        // Get an edge ID
        Edge edge = graphStore.findEdgesByRelationType("TREATS").blockFirst();
        assertThat(edge).isNotNull();

        StepVerifier.create(graphStore.deleteEdge(edge.getId()))
                .verifyComplete();

        StepVerifier.create(graphStore.getEdge(edge.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    // ==================== Batch Operations Tests ====================

    @Test
    @Order(12)
    @DisplayName("Should batch add nodes efficiently")
    void shouldBatchAddNodes() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            nodes.add(new Node(UUID.randomUUID(), "TestNode", 
                    Map.of("index", i, "name", "Node " + i), 
                    "Test node " + i));
        }

        long start = System.currentTimeMillis();
        
        StepVerifier.create(graphStore.batchAddNodes(nodes).collectList())
                .assertNext(saved -> {
                    assertThat(saved).hasSize(100);
                })
                .verifyComplete();

        long duration = System.currentTimeMillis() - start;
        System.out.println("Batch inserted 100 nodes in " + duration + "ms");
        
        // Verify all nodes exist
        StepVerifier.create(graphStore.findNodesByLabel("TestNode").collectList())
                .assertNext(found -> assertThat(found).hasSize(100))
                .verifyComplete();
    }

    @Test
    @Order(13)
    @DisplayName("Should batch add edges efficiently")
    void shouldBatchAddEdges() {
        // First create nodes
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            nodes.add(new Node(UUID.randomUUID(), "BatchPatient", Map.of("index", i), null));
        }
        graphStore.batchAddNodes(nodes).collectList().block();

        // Create edges connecting sequential nodes
        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            edges.add(new Edge(UUID.randomUUID(), "TREATS", 
                    nodes.get(i).getId(), nodes.get(i + 1).getId(), 
                    Map.of("order", i), 1.0));
        }

        StepVerifier.create(graphStore.batchAddEdges(edges).collectList())
                .assertNext(saved -> {
                    assertThat(saved).hasSize(19);
                })
                .verifyComplete();
    }

    // ==================== Statistics Tests ====================

    @Test
    @Order(14)
    @DisplayName("Should return accurate graph statistics")
    void shouldReturnGraphStatistics() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.getStatistics())
                .assertNext(stats -> {
                    assertThat(stats.getNodeCount()).isGreaterThan(0);
                    assertThat(stats.getEdgeCount()).isGreaterThan(0);
                    assertThat(stats.getNodeCountByLabel()).isNotEmpty();
                    assertThat(stats.getEdgeCountByType()).isNotEmpty();
                    assertThat(stats.getAverageDegree()).isGreaterThan(0);
                    
                    System.out.println("Graph Statistics:");
                    System.out.println("  Nodes: " + stats.getNodeCount());
                    System.out.println("  Edges: " + stats.getEdgeCount());
                    System.out.println("  Avg Degree: " + stats.getAverageDegree());
                    System.out.println("  By Label: " + stats.getNodeCountByLabel());
                    System.out.println("  By Type: " + stats.getEdgeCountByType());
                })
                .verifyComplete();
    }

    // ==================== Concurrent Access Tests ====================

    @Test
    @Order(15)
    @DisplayName("Should handle concurrent node insertions")
    void shouldHandleConcurrentNodeInsertions() throws InterruptedException {
        int threadCount = 10;
        int nodesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < nodesPerThread; i++) {
                        Node node = new Node(UUID.randomUUID(), "ConcurrentNode", 
                                Map.of("thread", threadId, "index", i), null);
                        graphStore.addNode(node).block();
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount * nodesPerThread);
        
        // Verify all nodes were inserted
        StepVerifier.create(graphStore.findNodesByLabel("ConcurrentNode").collectList())
                .assertNext(nodes -> assertThat(nodes).hasSize(threadCount * nodesPerThread))
                .verifyComplete();
    }

    @Test
    @Order(16)
    @DisplayName("Should handle concurrent reads and writes")
    void shouldHandleConcurrentReadsAndWrites() throws InterruptedException {
        // Pre-populate with some data
        setupMedicalGraph();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);

        // Half threads read, half write
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    if (threadId % 2 == 0) {
                        // Read operations
                        for (int i = 0; i < 20; i++) {
                            graphStore.getStatistics().block();
                            graphStore.findNodesByLabel("Patient").collectList().block();
                            readCount.incrementAndGet();
                        }
                    } else {
                        // Write operations
                        for (int i = 0; i < 10; i++) {
                            Node node = new Node(UUID.randomUUID(), "MixedNode",
                                    Map.of("thread", threadId, "index", i), null);
                            graphStore.addNode(node).block();
                            writeCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(60, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        System.out.println("Completed reads: " + readCount.get() + ", writes: " + writeCount.get());
        assertThat(readCount.get()).isEqualTo(4 * 20); // 4 reader threads * 20 iterations
        assertThat(writeCount.get()).isEqualTo(4 * 10); // 4 writer threads * 10 iterations
    }

    // ==================== Health Check Tests ====================

    @Test
    @Order(17)
    @DisplayName("Should report healthy status")
    void shouldReportHealthyStatus() {
        assertThat(graphStore.isHealthy()).isTrue();
        assertThat(graphStore.isInitialized()).isTrue();
    }

    // ==================== Clear Tests ====================

    @Test
    @Order(99)
    @DisplayName("Should clear all data")
    void shouldClearAllData() {
        setupMedicalGraph();

        StepVerifier.create(graphStore.clear())
                .verifyComplete();

        StepVerifier.create(graphStore.getStatistics())
                .assertNext(stats -> {
                    assertThat(stats.getNodeCount()).isZero();
                    assertThat(stats.getEdgeCount()).isZero();
                })
                .verifyComplete();
    }

    // ==================== Helper Methods ====================

    /**
     * Sets up a medical knowledge graph for testing.
     * 
     * Graph structure:
     * Doctor --TREATS--> Patient1 --HAS_CONDITION--> Disease
     *                    Patient1 --PRESCRIBED--> Medication
     * Doctor --TREATS--> Patient2
     */
    private void setupMedicalGraph() {
        // Create nodes
        graphStore.addNode(new Node(DOCTOR, "Doctor", 
                Map.of("name", "Dr. Smith", "specialty", "Internal Medicine"), 
                "Dr. Smith, Internal Medicine specialist")).block();
        
        graphStore.addNode(new Node(PATIENT_1, "Patient", 
                Map.of("name", "John Doe", "age", 45), 
                "Patient John Doe, 45 years old")).block();
        
        graphStore.addNode(new Node(PATIENT_2, "Patient", 
                Map.of("name", "Jane Smith", "age", 32), 
                "Patient Jane Smith, 32 years old")).block();
        
        graphStore.addNode(new Node(DISEASE, "Disease", 
                Map.of("name", "Diabetes Type 2", "icd10", "E11"), 
                "Diabetes Type 2, chronic condition")).block();
        
        graphStore.addNode(new Node(MEDICATION, "Medication", 
                Map.of("name", "Metformin", "dosage", "500mg"), 
                "Metformin 500mg, diabetes medication")).block();

        // Create edges
        graphStore.addEdge(new Edge(UUID.randomUUID(), "TREATS", DOCTOR, PATIENT_1, 
                Map.of("since", "2024-01-01"), 1.0)).block();
        
        graphStore.addEdge(new Edge(UUID.randomUUID(), "TREATS", DOCTOR, PATIENT_2, 
                Map.of("since", "2024-03-15"), 1.0)).block();
        
        graphStore.addEdge(new Edge(UUID.randomUUID(), "HAS_CONDITION", PATIENT_1, DISEASE, 
                Map.of("diagnosedDate", "2023-06-01"), 1.0)).block();
        
        graphStore.addEdge(new Edge(UUID.randomUUID(), "PRESCRIBED", PATIENT_1, MEDICATION, 
                Map.of("startDate", "2023-06-01", "frequency", "daily"), 1.0)).block();
    }
}
