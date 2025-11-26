package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.Chunk;
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
class ChunkRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
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
    private ChunkRepository chunkRepository;

    @Autowired
    private NodeRepository nodeRepository;

    @BeforeEach
    void setUp() {
        chunkRepository.deleteAll().block();
        nodeRepository.deleteAll().block();
    }

    @Test
    void shouldCreateChunk() {
        // Given
        Chunk chunk = createTestChunk("Diabetes is a metabolic disease");

        // When & Then
        StepVerifier.create(chunkRepository.save(chunk))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getContent()).isEqualTo("Diabetes is a metabolic disease");
                    assertThat(saved.getCreated()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldFindChunkById() {
        // Given
        Chunk chunk = createTestChunk("Medical guidelines for treatment");
        Chunk saved = chunkRepository.save(chunk).block();

        // When & Then
        StepVerifier.create(chunkRepository.findById(saved.getId()))
                .assertNext(found -> {
                    assertThat(found.getContent()).isEqualTo("Medical guidelines for treatment");
                })
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.Disabled("R2DBC vector type encoding not yet supported - requires custom codec. See TEST_ARCHITECTURE.md")
    void shouldSaveChunkWithEmbedding() {
        // Given
        Chunk chunk = createTestChunk("Test content");
        chunk.setEmbedding(new float[] { 0.1f, 0.2f, 0.3f });

        // When & Then
        // TODO: This test will pass once we implement VectorCodec for R2DBC
        // See: https://github.com/pgjdbc/r2dbc-postgresql/issues/XXX
        StepVerifier.create(chunkRepository.save(chunk))
                .assertNext(saved -> {
                    assertThat(saved.getEmbedding()).hasSize(3);
                    assertThat(saved.getEmbedding()[0]).isEqualTo(0.1f);
                })
                .verifyComplete();
    }

    @Test
    void shouldFindByLinkedNodeId() {
        // Given
        org.ddse.ml.cef.domain.Node node = new org.ddse.ml.cef.domain.Node();
        node.setLabel("LinkedNode");
        node.setProperties(new java.util.HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        org.ddse.ml.cef.domain.Node otherNode = new org.ddse.ml.cef.domain.Node();
        otherNode.setLabel("OtherNode");
        otherNode.setProperties(new java.util.HashMap<>());
        UUID otherNodeId = nodeRepository.save(otherNode).block().getId();

        Chunk chunk1 = createTestChunk("Chunk 1");
        chunk1.setLinkedNodeId(nodeId);
        chunkRepository.save(chunk1).block();

        Chunk chunk2 = createTestChunk("Chunk 2");
        chunk2.setLinkedNodeId(nodeId);
        chunkRepository.save(chunk2).block();

        Chunk chunk3 = createTestChunk("Chunk 3");
        chunk3.setLinkedNodeId(otherNodeId);
        chunkRepository.save(chunk3).block();

        // When & Then
        StepVerifier.create(chunkRepository.findByLinkedNodeId(nodeId))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldSaveChunkWithMetadata() {
        // Given
        Chunk chunk = createTestChunk("Content with metadata");
        chunk.setMetadata(Map.of(
                "source", "medical_journal.pdf",
                "page", 42,
                "topic", "cardiology"));

        // When & Then
        StepVerifier.create(chunkRepository.save(chunk))
                .assertNext(saved -> {
                    assertThat(saved.getMetadata()).containsEntry("source", "medical_journal.pdf");
                    assertThat(saved.getMetadata()).containsEntry("page", 42);
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteChunk() {
        // Given
        Chunk chunk = createTestChunk("Temporary content");
        Chunk saved = chunkRepository.save(chunk).block();

        // When
        StepVerifier.create(chunkRepository.deleteById(saved.getId()))
                .verifyComplete();

        // Then
        StepVerifier.create(chunkRepository.findById(saved.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldHandleNullLinkedNodeId() {
        // Given
        Chunk chunk = createTestChunk("Unlinked content");
        chunk.setLinkedNodeId(null);

        // When & Then
        StepVerifier.create(chunkRepository.save(chunk))
                .assertNext(saved -> assertThat(saved.getLinkedNodeId()).isNull())
                .verifyComplete();
    }

    private Chunk createTestChunk(String content) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.randomUUID());
        chunk.setContent(content);
        chunk.setMetadata(new HashMap<>());
        chunk.setCreated(Instant.now());
        return chunk;
    }
}
