package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.Chunk;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for Chunk entities.
 * Provides vector similarity search using database-native extensions.
 *
 * @author mrmanna
 */
@Repository
public interface ChunkRepository extends R2dbcRepository<Chunk, UUID> {

    /**
     * Find chunks linked to a specific node.
     */
    Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId);

    /**
     * Find chunks without embeddings (for batch processing).
     */
    @Query("SELECT * FROM chunks WHERE embedding IS NULL")
    Flux<Chunk> findChunksWithoutEmbeddings();

    /**
     * PostgreSQL: Vector similarity search using pgvector.
     * Uses cosine similarity (<=> operator).
     */
    @Query("SELECT *, 1 - (embedding <=> :queryVector::vector) AS similarity " +
            "FROM chunks " +
            "WHERE embedding IS NOT NULL " +
            "ORDER BY embedding <=> :queryVector::vector " +
            "LIMIT :topK")
    Flux<Chunk> findTopKSimilarPg(@Param("queryVector") float[] queryVector,
            @Param("topK") int topK);

    /**
     * DuckDB: Vector similarity search using array_cosine_similarity.
     */
    @Query("SELECT *, array_cosine_similarity(embedding, :queryVector) AS similarity " +
            "FROM chunks " +
            "WHERE embedding IS NOT NULL " +
            "ORDER BY array_cosine_similarity(embedding, :queryVector) DESC " +
            "LIMIT :topK")
    Flux<Chunk> findTopKSimilarDuck(@Param("queryVector") float[] queryVector,
            @Param("topK") int topK);

    /**
     * Hybrid search: Vector similarity + graph filtering.
     * Returns chunks linked to specific node labels.
     */
    @Query("SELECT c.*, 1 - (c.embedding <=> :queryVector::vector) AS similarity " +
            "FROM chunks c " +
            "INNER JOIN nodes n ON c.linked_node_id = n.id " +
            "WHERE n.label = :nodeLabel AND c.embedding IS NOT NULL " +
            "ORDER BY c.embedding <=> :queryVector::vector " +
            "LIMIT :topK")
    Flux<Chunk> findTopKSimilarWithNodeLabelPg(@Param("queryVector") float[] queryVector,
            @Param("nodeLabel") String nodeLabel,
            @Param("topK") int topK);

    /**
     * Count chunks linked to a specific node.
     */
    @Query("SELECT COUNT(*) FROM chunks WHERE linked_node_id = :nodeId")
    Mono<Long> countByLinkedNodeId(@Param("nodeId") UUID nodeId);

    /**
     * Delete all chunks linked to a node.
     */
    @Query("DELETE FROM chunks WHERE linked_node_id = :nodeId")
    Mono<Void> deleteByLinkedNodeId(@Param("nodeId") UUID nodeId);
}
