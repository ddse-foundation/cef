package org.ddse.ml.cef.repository.duckdb;

import org.ddse.ml.cef.DuckDBTestConfiguration;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.repository.EdgeRepository;
import org.ddse.ml.cef.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DuckDB-specific tests for EdgeRepository.
 * 
 * @author mrmanna
 */
@DataR2dbcTest
@Import(DuckDBTestConfiguration.class)
@ActiveProfiles("duckdb")
class EdgeRepositoryDuckDBTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DuckDB JDBC connection will be used via wrapper
        registry.add("spring.r2dbc.username", () -> "sa");
        registry.add("spring.r2dbc.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "embedded");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-duckdb-test.sql");
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
        Node sourceNode = new Node();
        sourceNode.setLabel("Source");
        sourceNode.setProperties(new HashMap<>());
        sourceNode = nodeRepository.save(sourceNode).block();
        sourceNodeId = sourceNode.getId();

        Node targetNode = new Node();
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
        Node node1 = new Node();
        node1.setLabel("Node1");
        node1.setProperties(new HashMap<>());
        UUID node1Id = nodeRepository.save(node1).block().getId();

        Node node2 = new Node();
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
        Node node1 = new Node();
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
        Node node1 = new Node();
        node1.setLabel("Node1");
        node1.setProperties(new HashMap<>());
        UUID node1Id = nodeRepository.save(node1).block().getId();

        Node node2 = new Node();
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
        Node node = new Node();
        node.setLabel("TestNode");
        node.setProperties(new HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        Node otherNode1 = new Node();
        otherNode1.setLabel("Other1");
        otherNode1.setProperties(new HashMap<>());
        UUID otherId1 = nodeRepository.save(otherNode1).block().getId();

        Node otherNode2 = new Node();
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
        Node node = new Node();
        node.setLabel("TestNode");
        node.setProperties(new HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        Node otherNode1 = new Node();
        otherNode1.setLabel("Other1");
        otherNode1.setProperties(new HashMap<>());
        UUID otherId1 = nodeRepository.save(otherNode1).block().getId();

        Node otherNode2 = new Node();
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
