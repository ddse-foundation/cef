package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.Edge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
class EdgeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private NodeRepository nodeRepository;

    private UUID sourceNodeId;
    private UUID targetNodeId;

    @BeforeEach
    void setUp() {
        edgeRepository.deleteAll().block();
        nodeRepository.deleteAll().block();

        // Create nodes first to satisfy foreign key constraints
        org.ddse.ml.cef.domain.Node sourceNode = new org.ddse.ml.cef.domain.Node();
        sourceNode.setLabel("Source");
        sourceNode.setProperties(new HashMap<>());
        sourceNode = nodeRepository.save(sourceNode).block();
        sourceNodeId = sourceNode.getId();

        org.ddse.ml.cef.domain.Node targetNode = new org.ddse.ml.cef.domain.Node();
        targetNode.setLabel("Target");
        targetNode.setProperties(new HashMap<>());
        targetNode = nodeRepository.save(targetNode).block();
        targetNodeId = targetNode.getId();
    }

    @Test
    void shouldCreateEdge() {
        // Given
        Edge edge = createTestEdge("TREATS", sourceNodeId, targetNodeId);

        // When & Then
        StepVerifier.create(edgeRepository.save(edge))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getRelationType()).isEqualTo("TREATS");
                    assertThat(saved.getSourceNodeId()).isEqualTo(sourceNodeId);
                    assertThat(saved.getTargetNodeId()).isEqualTo(targetNodeId);
                })
                .verifyComplete();
    }

    @Test
    void shouldFindBySourceNodeId() {
        // Given
        org.ddse.ml.cef.domain.Node node1 = new org.ddse.ml.cef.domain.Node();
        node1.setLabel("Node1");
        node1.setProperties(new HashMap<>());
        UUID node1Id = nodeRepository.save(node1).block().getId();

        org.ddse.ml.cef.domain.Node node2 = new org.ddse.ml.cef.domain.Node();
        node2.setLabel("Node2");
        node2.setProperties(new HashMap<>());
        UUID node2Id = nodeRepository.save(node2).block().getId();

        edgeRepository.save(createTestEdge("TREATS", sourceNodeId, targetNodeId)).block();
        edgeRepository.save(createTestEdge("PRESCRIBES", sourceNodeId, node1Id)).block();
        edgeRepository.save(createTestEdge("HAS_CONDITION", node2Id, targetNodeId)).block();

        // When & Then
        StepVerifier.create(edgeRepository.findBySourceNodeId(sourceNodeId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldFindByTargetNodeId() {
        // Given
        org.ddse.ml.cef.domain.Node node1 = new org.ddse.ml.cef.domain.Node();
        node1.setLabel("Node1");
        node1.setProperties(new HashMap<>());
        UUID node1Id = nodeRepository.save(node1).block().getId();

        edgeRepository.save(createTestEdge("TREATS", sourceNodeId, targetNodeId)).block();
        edgeRepository.save(createTestEdge("PRESCRIBES", node1Id, targetNodeId)).block();

        // When & Then
        StepVerifier.create(edgeRepository.findByTargetNodeId(targetNodeId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldFindByRelationType() {
        // Given
        org.ddse.ml.cef.domain.Node node1 = new org.ddse.ml.cef.domain.Node();
        node1.setLabel("Node1");
        node1.setProperties(new HashMap<>());
        UUID node1Id = nodeRepository.save(node1).block().getId();

        org.ddse.ml.cef.domain.Node node2 = new org.ddse.ml.cef.domain.Node();
        node2.setLabel("Node2");
        node2.setProperties(new HashMap<>());
        UUID node2Id = nodeRepository.save(node2).block().getId();

        edgeRepository.save(createTestEdge("TREATS", sourceNodeId, targetNodeId)).block();
        edgeRepository.save(createTestEdge("TREATS", node1Id, node2Id)).block();
        edgeRepository.save(createTestEdge("PRESCRIBES", sourceNodeId, targetNodeId)).block();

        // When & Then
        StepVerifier.create(edgeRepository.findByRelationType("TREATS"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldFindByNodeId() {
        // Given
        org.ddse.ml.cef.domain.Node node = new org.ddse.ml.cef.domain.Node();
        node.setLabel("TestNode");
        node.setProperties(new HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        org.ddse.ml.cef.domain.Node otherNode1 = new org.ddse.ml.cef.domain.Node();
        otherNode1.setLabel("Other1");
        otherNode1.setProperties(new HashMap<>());
        UUID otherId1 = nodeRepository.save(otherNode1).block().getId();

        org.ddse.ml.cef.domain.Node otherNode2 = new org.ddse.ml.cef.domain.Node();
        otherNode2.setLabel("Other2");
        otherNode2.setProperties(new HashMap<>());
        UUID otherId2 = nodeRepository.save(otherNode2).block().getId();

        edgeRepository.save(createTestEdge("TREATS", nodeId, targetNodeId)).block();
        edgeRepository.save(createTestEdge("PRESCRIBES", sourceNodeId, nodeId)).block();
        edgeRepository.save(createTestEdge("HAS_CONDITION", otherId1, otherId2)).block();

        // When & Then
        StepVerifier.create(edgeRepository.findByNodeId(nodeId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldDeleteByNodeId() {
        // Given
        org.ddse.ml.cef.domain.Node node = new org.ddse.ml.cef.domain.Node();
        node.setLabel("TestNode");
        node.setProperties(new HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        org.ddse.ml.cef.domain.Node otherNode1 = new org.ddse.ml.cef.domain.Node();
        otherNode1.setLabel("Other1");
        otherNode1.setProperties(new HashMap<>());
        UUID otherId1 = nodeRepository.save(otherNode1).block().getId();

        org.ddse.ml.cef.domain.Node otherNode2 = new org.ddse.ml.cef.domain.Node();
        otherNode2.setLabel("Other2");
        otherNode2.setProperties(new HashMap<>());
        UUID otherId2 = nodeRepository.save(otherNode2).block().getId();

        edgeRepository.save(createTestEdge("TREATS", nodeId, targetNodeId)).block();
        edgeRepository.save(createTestEdge("PRESCRIBES", sourceNodeId, nodeId)).block();
        edgeRepository.save(createTestEdge("HAS_CONDITION", otherId1, otherId2)).block();

        // When
        StepVerifier.create(edgeRepository.deleteByNodeId(nodeId))
                .verifyComplete();

        // Then
        StepVerifier.create(edgeRepository.findByNodeId(nodeId))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldSaveEdgeWithProperties() {
        // Given
        Edge edge = createTestEdge("TREATS", sourceNodeId, targetNodeId);
        edge.setProperties(Map.of("since", "2024-01-01", "primary", true));

        // When & Then
        StepVerifier.create(edgeRepository.save(edge))
                .assertNext(saved -> {
                    assertThat(saved.getProperties()).containsEntry("since", "2024-01-01");
                    assertThat(saved.getProperties()).containsEntry("primary", true);
                })
                .verifyComplete();
    }

    @Test
    void shouldSaveEdgeWithWeight() {
        // Given
        Edge edge = createTestEdge("TREATS", sourceNodeId, targetNodeId);
        edge.setWeight(0.85);

        // When & Then
        StepVerifier.create(edgeRepository.save(edge))
                .assertNext(saved -> assertThat(saved.getWeight()).isEqualTo(0.85))
                .verifyComplete();
    }

    private Edge createTestEdge(String relationType, UUID sourceId, UUID targetId) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID());
        edge.setRelationType(relationType);
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setProperties(new HashMap<>());
        edge.setCreated(Instant.now());
        return edge;
    }
}
