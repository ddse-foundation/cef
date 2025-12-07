package org.ddse.ml.cef.repository.inmemory;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ChunkStore for development and testing.
 * Uses ConcurrentHashMap for thread-safe storage and brute-force cosine similarity for vector search.
 * 
 * <p><b>Configuration:</b></p>
 * <pre>
 * cef:
 *   vector:
 *     store: in-memory
 * </pre>
 * 
 * <p><b>Characteristics:</b></p>
 * <ul>
 *   <li>Zero external dependencies - no database required</li>
 *   <li>Fast for small datasets (&lt;10k chunks)</li>
 *   <li>Data is lost on application restart (non-persistent)</li>
 *   <li>Brute-force O(n) similarity search</li>
 * </ul>
 * 
 * <p><b>Use Cases:</b></p>
 * <ul>
 *   <li>Unit testing without database setup</li>
 *   <li>Local development and prototyping</li>
 *   <li>CI/CD pipelines where database isn't available</li>
 *   <li>Small demos and POCs</li>
 * </ul>
 * 
 * <p>For production or larger datasets, use {@code duckdb}, {@code postgresql}, or {@code neo4j}.</p>
 *
 * <p><b>Note:</b> This class is NOT a @Component. It is created by ChunkStoreAutoConfiguration
 * when cef.vector.store=in-memory is set.</p>
 *
 * @author mrmanna
 * @since v0.6
 */
public class InMemoryChunkStore implements ChunkStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryChunkStore.class);

    /** Primary storage: chunk ID -> Chunk */
    private final ConcurrentHashMap<UUID, Chunk> chunks = new ConcurrentHashMap<>();

    /** Index: linked node ID -> set of chunk IDs (for fast lookup) */
    private final ConcurrentHashMap<UUID, Set<UUID>> nodeToChunks = new ConcurrentHashMap<>();

    public InMemoryChunkStore() {
        log.info("InMemoryChunkStore initialized (zero-dependency mode)");
        log.warn("InMemoryChunkStore is non-persistent - data will be lost on restart");
    }

    @Override
    public Mono<Chunk> save(Chunk chunk) {
        return Mono.fromCallable(() -> {
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }

            // Store chunk
            chunks.put(chunk.getId(), chunk);

            // Update node index
            if (chunk.getLinkedNodeId() != null) {
                nodeToChunks
                        .computeIfAbsent(chunk.getLinkedNodeId(), k -> ConcurrentHashMap.newKeySet())
                        .add(chunk.getId());
            }

            log.debug("Saved chunk {} linked to node {}", chunk.getId(), chunk.getLinkedNodeId());
            return chunk;
        });
    }

    @Override
    public Flux<Chunk> findTopKSimilar(float[] queryVector, int topK) {
        return Flux.defer(() -> {
            if (queryVector == null || queryVector.length == 0) {
                return Flux.empty();
            }

            // Brute-force cosine similarity search
            List<ScoredChunk> scored = chunks.values().stream()
                    .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                    .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryVector, chunk.getEmbedding())))
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(topK)
                    .toList();

            log.debug("Found {} similar chunks (searched {} total)", scored.size(), chunks.size());

            return Flux.fromIterable(scored.stream().map(ScoredChunk::chunk).toList());
        });
    }

    @Override
    public Flux<Chunk> findTopKSimilarWithNodeLabel(float[] queryVector, String nodeLabel, int topK) {
        return Flux.defer(() -> {
            if (queryVector == null || queryVector.length == 0) {
                return Flux.empty();
            }

            // Filter by node label in metadata, then sort by similarity
            List<ScoredChunk> scored = chunks.values().stream()
                    .filter(chunk -> chunk.getEmbedding() != null && chunk.getEmbedding().length > 0)
                    .filter(chunk -> matchesNodeLabel(chunk, nodeLabel))
                    .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryVector, chunk.getEmbedding())))
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(topK)
                    .toList();

            log.debug("Found {} similar chunks with label '{}' (searched {} total)", 
                    scored.size(), nodeLabel, chunks.size());

            return Flux.fromIterable(scored.stream().map(ScoredChunk::chunk).toList());
        });
    }

    @Override
    public Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId) {
        return Flux.defer(() -> {
            Set<UUID> chunkIds = nodeToChunks.get(linkedNodeId);
            if (chunkIds == null || chunkIds.isEmpty()) {
                return Flux.empty();
            }

            return Flux.fromIterable(
                    chunkIds.stream()
                            .map(chunks::get)
                            .filter(Objects::nonNull)
                            .toList()
            );
        });
    }

    @Override
    public Mono<Long> countByLinkedNodeId(UUID nodeId) {
        return Mono.fromCallable(() -> {
            Set<UUID> chunkIds = nodeToChunks.get(nodeId);
            return chunkIds != null ? (long) chunkIds.size() : 0L;
        });
    }

    @Override
    public Mono<Void> deleteByLinkedNodeId(UUID nodeId) {
        return Mono.fromRunnable(() -> {
            Set<UUID> chunkIds = nodeToChunks.remove(nodeId);
            if (chunkIds != null) {
                chunkIds.forEach(chunks::remove);
                log.debug("Deleted {} chunks linked to node {}", chunkIds.size(), nodeId);
            }
        });
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(() -> {
            int count = chunks.size();
            chunks.clear();
            nodeToChunks.clear();
            log.info("Deleted all {} chunks from in-memory store", count);
        });
    }

    // ==================== Helper Methods ====================

    /**
     * Compute cosine similarity between two vectors.
     * Returns value in range [-1, 1] where 1 = identical, 0 = orthogonal, -1 = opposite.
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            log.warn("Vector dimension mismatch: {} vs {}", a.length, b.length);
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0) {
            return 0.0;
        }

        return dotProduct / denominator;
    }

    /**
     * Check if chunk metadata contains matching node label.
     */
    private boolean matchesNodeLabel(Chunk chunk, String nodeLabel) {
        if (nodeLabel == null || nodeLabel.isEmpty()) {
            return true; // No filter
        }

        Map<String, Object> metadata = chunk.getMetadata();
        if (metadata == null) {
            return false;
        }

        // Check common metadata keys for node label
        Object label = metadata.get("nodeLabel");
        if (label == null) {
            label = metadata.get("label");
        }
        if (label == null) {
            label = metadata.get("type");
        }

        return nodeLabel.equalsIgnoreCase(String.valueOf(label));
    }

    /**
     * Internal record for scored chunk during similarity search.
     */
    private record ScoredChunk(Chunk chunk, double score) {}

    // ==================== Statistics / Debug ====================

    /**
     * Get current chunk count (for testing/debugging).
     */
    public int size() {
        return chunks.size();
    }

    /**
     * Get statistics about the store (for testing/debugging).
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChunks", chunks.size());
        stats.put("indexedNodes", nodeToChunks.size());
        stats.put("chunksWithEmbeddings", chunks.values().stream()
                .filter(c -> c.getEmbedding() != null && c.getEmbedding().length > 0)
                .count());
        return stats;
    }
}
