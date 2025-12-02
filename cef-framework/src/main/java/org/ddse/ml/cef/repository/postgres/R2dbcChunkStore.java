package org.ddse.ml.cef.repository.postgres;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * R2DBC implementation of ChunkStore (primarily for PostgreSQL).
 */
@Component
@ConditionalOnProperty(name = "cef.database.type", havingValue = "postgresql")
public class R2dbcChunkStore implements ChunkStore {

    private final ChunkRepository chunkRepository;

    public R2dbcChunkStore(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public Mono<Chunk> save(Chunk chunk) {
        return chunkRepository.save(chunk);
    }

    @Override
    public Flux<Chunk> findTopKSimilar(float[] queryVector, int topK) {
        return chunkRepository.findTopKSimilarPg(queryVector, topK);
    }

    @Override
    public Flux<Chunk> findTopKSimilarWithNodeLabel(float[] queryVector, String nodeLabel, int topK) {
        return chunkRepository.findTopKSimilarWithNodeLabelPg(queryVector, nodeLabel, topK);
    }

    @Override
    public Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId) {
        return chunkRepository.findByLinkedNodeId(linkedNodeId);
    }

    @Override
    public Mono<Long> countByLinkedNodeId(UUID nodeId) {
        return chunkRepository.countByLinkedNodeId(nodeId);
    }

    @Override
    public Mono<Void> deleteByLinkedNodeId(UUID nodeId) {
        return chunkRepository.deleteByLinkedNodeId(nodeId);
    }

    @Override
    public Mono<Void> deleteAll() {
        return chunkRepository.deleteAll();
    }
}
