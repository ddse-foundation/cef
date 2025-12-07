package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thread safety tests for ThreadSafeKnowledgeGraph.
 * 
 * Tests verify:
 * 1. Concurrent read operations don't corrupt data
 * 2. Write operations are exclusive and atomic
 * 3. No deadlocks under high contention
 * 4. Data consistency after concurrent modifications
 * 
 * @author mrmanna
 * @since 0.6
 */
@DisplayName("ThreadSafeKnowledgeGraph Concurrency Tests")
class ThreadSafeKnowledgeGraphTest {

    private InMemoryKnowledgeGraph delegate;
    private ThreadSafeKnowledgeGraph graph;
    
    @BeforeEach
    void setUp() {
        delegate = new InMemoryKnowledgeGraph();
        graph = new ThreadSafeKnowledgeGraph(delegate);
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("should add and find node")
        void shouldAddAndFindNode() {
            // Given
            Node node = createNode("Patient", "John Doe");

            // When
            graph.addNode(node);
            Optional<Node> found = graph.findNode(node.getId());

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(node.getId());
        }

        @Test
        @DisplayName("should add and remove node")
        void shouldAddAndRemoveNode() {
            // Given
            Node node = createNode("Patient", "John Doe");
            graph.addNode(node);

            // When
            graph.removeNode(node.getId());
            Optional<Node> found = graph.findNode(node.getId());

            // Then
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should add and find edge")
        void shouldAddAndFindEdge() {
            // Given
            Node patient = createNode("Patient", "John Doe");
            Node condition = createNode("Condition", "Diabetes");
            Edge edge = createEdge(patient.getId(), condition.getId(), "HAS_CONDITION");
            
            graph.addNode(patient);
            graph.addNode(condition);

            // When
            graph.addEdge(edge);
            Set<Edge> edges = graph.getEdges(patient.getId());

            // Then
            assertThat(edges).hasSize(1);
            assertThat(edges.iterator().next().getRelationType()).isEqualTo("HAS_CONDITION");
        }
    }

    @Nested
    @DisplayName("Concurrent Read Operations")
    class ConcurrentReadOperations {

        @RepeatedTest(5)
        @DisplayName("should handle concurrent reads without corruption")
        void shouldHandleConcurrentReads() throws Exception {
            // Given - populate graph with test data
            int nodeCount = 100;
            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < nodeCount; i++) {
                Node node = createNode("Patient", "Patient-" + i);
                nodes.add(node);
                graph.addNode(node);
            }

            int threadCount = 10;
            int readsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger errors = new AtomicInteger(0);

            // When - concurrent reads
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Random random = new Random();
                        for (int i = 0; i < readsPerThread; i++) {
                            Node randomNode = nodes.get(random.nextInt(nodes.size()));
                            Optional<Node> found = graph.findNode(randomNode.getId());
                            if (found.isPresent()) {
                                successfulReads.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(errors.get()).isZero();
            assertThat(successfulReads.get()).isEqualTo(threadCount * readsPerThread);
        }
    }

    @Nested
    @DisplayName("Concurrent Write Operations")
    class ConcurrentWriteOperations {

        @RepeatedTest(5)
        @DisplayName("should handle concurrent writes atomically")
        void shouldHandleConcurrentWrites() throws Exception {
            // Given
            int threadCount = 10;
            int writesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            // When - concurrent writes
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < writesPerThread; i++) {
                            Node node = createNode("Patient", "Patient-" + threadId + "-" + i);
                            graph.addNode(node);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(errors.get()).isZero();
            assertThat(graph.getNodeCount()).isEqualTo(threadCount * writesPerThread);
        }
    }

    @Nested
    @DisplayName("Mixed Read-Write Operations")
    class MixedReadWriteOperations {

        @RepeatedTest(5)
        @DisplayName("should handle concurrent reads and writes without data corruption")
        void shouldHandleMixedOperations() throws Exception {
            // Given - pre-populate with some data
            int initialNodes = 50;
            List<UUID> nodeIds = new CopyOnWriteArrayList<>();
            for (int i = 0; i < initialNodes; i++) {
                Node node = createNode("Patient", "Initial-" + i);
                graph.addNode(node);
                nodeIds.add(node.getId());
            }

            int readerCount = 5;
            int writerCount = 3;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(readerCount + writerCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(readerCount + writerCount);
            AtomicInteger readErrors = new AtomicInteger(0);
            AtomicInteger writeErrors = new AtomicInteger(0);
            AtomicInteger successfulReads = new AtomicInteger(0);
            AtomicInteger successfulWrites = new AtomicInteger(0);

            // Readers
            for (int r = 0; r < readerCount; r++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Random random = new Random();
                        for (int i = 0; i < operationsPerThread; i++) {
                            if (!nodeIds.isEmpty()) {
                                UUID id = nodeIds.get(random.nextInt(nodeIds.size()));
                                graph.findNode(id);
                                graph.getNodeCount();
                                successfulReads.incrementAndGet();
                            }
                            Thread.yield(); // Give writers a chance
                        }
                    } catch (Exception e) {
                        readErrors.incrementAndGet();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            // Writers
            for (int w = 0; w < writerCount; w++) {
                final int writerId = w;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            Node node = createNode("Patient", "Writer-" + writerId + "-" + i);
                            graph.addNode(node);
                            nodeIds.add(node.getId());
                            successfulWrites.incrementAndGet();
                            Thread.yield(); // Give readers a chance
                        }
                    } catch (Exception e) {
                        writeErrors.incrementAndGet();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = completeLatch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertThat(completed).isTrue();
            assertThat(readErrors.get()).isZero();
            assertThat(writeErrors.get()).isZero();
            
            // Verify final state consistency
            long expectedNodes = initialNodes + (writerCount * operationsPerThread);
            assertThat(graph.getNodeCount()).isEqualTo(expectedNodes);
        }
    }

    @Nested
    @DisplayName("Atomic Operations")
    class AtomicOperations {

        @Test
        @DisplayName("should add node with edges atomically")
        void shouldAddNodeWithEdgesAtomically() {
            // Given
            Node sourceNode = createNode("Patient", "John Doe");
            graph.addNode(sourceNode);

            Node targetNode = createNode("Condition", "Diabetes");
            List<Edge> edges = List.of(
                    createEdge(sourceNode.getId(), targetNode.getId(), "HAS_CONDITION")
            );

            // When
            graph.addNodeWithEdges(targetNode, edges);

            // Then
            assertThat(graph.findNode(targetNode.getId())).isPresent();
            assertThat(graph.getEdges(sourceNode.getId())).hasSize(1);
        }

        @Test
        @DisplayName("should add multiple nodes atomically")
        void shouldAddMultipleNodesAtomically() {
            // Given
            List<Node> nodes = IntStream.range(0, 10)
                    .mapToObj(i -> createNode("Patient", "Patient-" + i))
                    .toList();

            // When
            graph.addNodes(nodes);

            // Then
            assertThat(graph.getNodeCount()).isEqualTo(10);
            for (Node node : nodes) {
                assertThat(graph.findNode(node.getId())).isPresent();
            }
        }
    }

    @Nested
    @DisplayName("Lock Statistics")
    class LockStatistics {

        @Test
        @DisplayName("should report lock statistics")
        void shouldReportLockStatistics() {
            // Given
            graph.addNode(createNode("Patient", "Test"));

            // When
            Map<String, Object> stats = graph.getLockStatistics();

            // Then
            assertThat(stats).containsKeys("readLockCount", "isWriteLocked", "writeHoldCount", 
                    "hasQueuedThreads", "queueLength");
            assertThat((Boolean) stats.get("isWriteLocked")).isFalse();
        }
    }

    // ========== Helper Methods ==========

    private Node createNode(String label, String name) {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setLabel(label);
        node.setProperties(Map.of("name", name));
        return node;
    }

    private Edge createEdge(UUID sourceId, UUID targetId, String relationType) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationType(relationType);
        edge.setWeight(1.0);
        return edge;
    }
}
