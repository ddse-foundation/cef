package org.ddse.ml.cef.repository.duckdb;

import org.ddse.ml.cef.DuckDBTestConfiguration;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.repository.ChunkRepository;
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
 * DuckDB-specific tests for ChunkRepository.
 * 
 * @author mrmanna
 */
@DataR2dbcTest
@Import(DuckDBTestConfiguration.class)
@ActiveProfiles("duckdb")
class ChunkRepositoryDuckDBTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DuckDB JDBC connection will be used via wrapper
        registry.add("spring.r2dbc.username", () -> "sa");
        registry.add("spring.r2dbc.password", () -> "");
        registry.add("spring.sql.init.mode", () -> "embedded");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-duckdb-test.sql");
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
    void shouldSaveChunkWithEmbedding() {
        // Given
        Chunk chunk = createTestChunk("Test content");
        chunk.setEmbedding(new float[] { 0.1f, 0.2f, 0.3f });

        // When & Then
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
        Node node = new Node();
        node.setLabel("LinkedNode");
        node.setProperties(new HashMap<>());
        UUID nodeId = nodeRepository.save(node).block().getId();

        Node otherNode = new Node();
        otherNode.setLabel("OtherNode");
        otherNode.setProperties(new HashMap<>());
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
