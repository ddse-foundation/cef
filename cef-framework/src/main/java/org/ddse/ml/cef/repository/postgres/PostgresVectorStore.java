package org.ddse.ml.cef.repository.postgres;

import org.ddse.ml.cef.domain.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Vector store implementation using PostgreSQL with pgvector extension.
 * 
 * <p><b>Note:</b> This class is NOT a @Component. Use R2dbcChunkStore which is
 * created by ChunkStoreAutoConfiguration when cef.vector.store=postgresql.</p>
 * 
 * Prerequisites:
 * - PostgreSQL 12+
 * - pgvector extension installed: CREATE EXTENSION vector;
 * - Vector column: ALTER TABLE chunks ADD COLUMN embedding vector(1536);
 * - HNSW index: CREATE INDEX ON chunks USING hnsw (embedding
 * vector_cosine_ops);
 *
 * @author mrmanna
 */
public class PostgresVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresVectorStore.class);

    private final ChunkRepository chunkRepository;

    public PostgresVectorStore(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public Mono<Void> initialize(int dimension) {
        log.info("Initializing PostgreSQL vector store with dimension: {}", dimension);
        // Schema initialization handled by R2DBC migrations
        return Mono.empty();
    }

    @Override
    public Mono<Chunk> storeChunk(Chunk chunk) {
        return chunkRepository.save(chunk)
                .doOnSuccess(c -> log.debug("Stored chunk: {}", c.getId()));
    }

    @Override
    public Flux<Chunk> storeChunks(List<Chunk> chunks) {
        return chunkRepository.saveAll(chunks)
                .doOnComplete(() -> log.info("Stored {} chunks", chunks.size()));
    }

    @Override
    public Flux<Chunk> findSimilar(float[] queryEmbedding, int topK) {
        return chunkRepository.findTopKSimilarPg(queryEmbedding, topK)
                .doOnComplete(() -> log.debug("Found top-{} similar chunks", topK));
    }

    @Override
    public Flux<Chunk> findSimilarWithFilter(float[] queryEmbedding, int topK, ChunkFilter filter) {
        if (filter.getNodeLabel() != null) {
            return chunkRepository.findTopKSimilarWithNodeLabelPg(
                    queryEmbedding,
                    filter.getNodeLabel(),
                    topK);
        }

        // If only linkedNodeId filter, post-filter in memory
        return findSimilar(queryEmbedding, topK * 2) // Fetch 2x for filtering
                .filter(chunk -> filter.getLinkedNodeId() == null ||
                        filter.getLinkedNodeId().equals(chunk.getLinkedNodeId()))
                .take(topK);
    }

    @Override
    public Mono<Chunk> getChunk(UUID chunkId) {
        return chunkRepository.findById(chunkId);
    }

    @Override
    public Mono<Void> deleteChunk(UUID chunkId) {
        return chunkRepository.deleteById(chunkId);
    }

    @Override
    public Mono<Void> deleteChunksByNodeId(UUID nodeId) {
        return chunkRepository.deleteByLinkedNodeId(nodeId);
    }

    @Override
    public Mono<VectorStats> getStatistics() {
        return Mono.zip(
                chunkRepository.count(),
                chunkRepository.findAll()
                        .filter(c -> c.getEmbedding() != null && c.getEmbedding().length > 0)
                        .count())
                .map(tuple -> new VectorStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        1536, // Default OpenAI dimension
                        "pgvector-hnsw"));
    }

    @Override
    public Mono<Void> clear() {
        log.warn("Clearing all vectors from PostgreSQL");
        return chunkRepository.deleteAll();
    }
}
