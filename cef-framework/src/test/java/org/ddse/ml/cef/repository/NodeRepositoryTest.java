package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.Node;
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
class NodeRepositoryTest {

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
    private NodeRepository nodeRepository;

    @BeforeEach
    void setUp() {
        nodeRepository.deleteAll().block();
    }

    @Test
    void shouldCreateNode() {
        // Given
        Node node = createTestNode("Patient", Map.of("name", "John Doe", "age", 45));

        // When & Then
        StepVerifier.create(nodeRepository.save(node))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getLabel()).isEqualTo("Patient");
                    assertThat(saved.getProperties()).containsEntry("name", "John Doe");
                    assertThat(saved.getCreated()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldFindNodeById() {
        // Given
        Node node = createTestNode("Doctor", Map.of("name", "Dr. Smith"));
        Node saved = nodeRepository.save(node).block();

        // When & Then
        StepVerifier.create(nodeRepository.findById(saved.getId()))
                .assertNext(found -> {
                    assertThat(found.getLabel()).isEqualTo("Doctor");
                    assertThat(found.getProperties()).containsEntry("name", "Dr. Smith");
                })
                .verifyComplete();
    }

    @Test
    void shouldFindNodesByLabel() {
        // Given
        nodeRepository.save(createTestNode("Patient", Map.of("name", "John"))).block();
        nodeRepository.save(createTestNode("Patient", Map.of("name", "Jane"))).block();
        nodeRepository.save(createTestNode("Doctor", Map.of("name", "Dr. Smith"))).block();

        // When & Then
        StepVerifier.create(nodeRepository.findByLabel("Patient"))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldUpdateNode() {
        // Given
        Node node = createTestNode("Patient", Map.of("name", "John", "age", 45));
        Node saved = nodeRepository.save(node).block();

        // When
        saved.setNew(false); // Mark as existing entity
        saved.getProperties().put("age", 46);
        saved.setUpdated(Instant.now());

        // Then
        StepVerifier.create(nodeRepository.save(saved))
                .assertNext(updated -> {
                    assertThat(updated.getProperties()).containsEntry("age", 46);
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteNode() {
        // Given
        Node node = createTestNode("Patient", Map.of("name", "John"));
        Node saved = nodeRepository.save(node).block();

        // When
        StepVerifier.create(nodeRepository.deleteById(saved.getId()))
                .verifyComplete();

        // Then
        StepVerifier.create(nodeRepository.findById(saved.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldCountByLabel() {
        // Given
        nodeRepository.save(createTestNode("Patient", Map.of("name", "John"))).block();
        nodeRepository.save(createTestNode("Patient", Map.of("name", "Jane"))).block();
        nodeRepository.save(createTestNode("Doctor", Map.of("name", "Dr. Smith"))).block();

        // When & Then
        StepVerifier.create(nodeRepository.countByLabel("Patient"))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void shouldFindNodeWithVectorizableContent() {
        // Given
        Node node1 = createTestNode("Patient", Map.of("name", "John"));
        node1.setVectorizableContent("Patient John with diabetes");
        nodeRepository.save(node1).block();

        Node node2 = createTestNode("Doctor", Map.of("name", "Dr. Smith"));
        // No vectorizable content
        nodeRepository.save(node2).block();

        // When & Then - find all and filter
        StepVerifier.create(nodeRepository.findAll()
                .filter(n -> n.getVectorizableContent() != null))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldHandleNullProperties() {
        // Given
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setLabel("Empty");
        node.setProperties(new HashMap<>());
        node.setCreated(Instant.now());
        node.setUpdated(Instant.now());

        // When & Then
        StepVerifier.create(nodeRepository.save(node))
                .assertNext(saved -> assertThat(saved.getProperties()).isEmpty())
                .verifyComplete();
    }

    private Node createTestNode(String label, Map<String, Object> properties) {
        Node node = new Node();
        node.setId(UUID.randomUUID());
        node.setLabel(label);
        node.setProperties(new HashMap<>(properties));
        node.setCreated(Instant.now());
        node.setUpdated(Instant.now());
        return node;
    }
}
