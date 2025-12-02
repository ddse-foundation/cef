package org.ddse.ml.cef.repository.duckdb;

import org.ddse.ml.cef.domain.Chunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Common interface for Chunk storage operations, abstracting over R2DBC
 * (Postgres) and JDBC (DuckDB).
 */
public interface ChunkStore {
    Mono<Chunk> save(Chunk chunk);

    Flux<Chunk> findTopKSimilar(float[] queryVector, int topK);

    Flux<Chunk> findTopKSimilarWithNodeLabel(float[] queryVector, String nodeLabel, int topK);

    Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId);

    Mono<Long> countByLinkedNodeId(UUID nodeId);

    Mono<Void> deleteByLinkedNodeId(UUID nodeId);

    Mono<Void> deleteAll();
}
