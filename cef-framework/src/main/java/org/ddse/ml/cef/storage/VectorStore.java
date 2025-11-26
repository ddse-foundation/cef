package org.ddse.ml.cef.storage;

import org.ddse.ml.cef.domain.Chunk;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Pluggable interface for vector storage backends.
 * 
 * Implementations:
 * - PostgresVectorStore: pgvector extension (default)
 * - DuckDBVectorStore: DuckDB with vector functions
 * - QdrantVectorStore: Qdrant vector database
 * - PineconeVectorStore: Pinecone managed service
 * 
 * Framework selects implementation based on application.yml:
 * cef.vector.store: postgres | duckdb | qdrant | pinecone
 *
 * @author mrmanna
 */
public interface VectorStore {

    /**
     * Initialize vector store (create indexes, configure dimensions).
     * Must be called before any vector operations.
     */
    Mono<Void> initialize(int dimension);

    /**
     * Store a chunk with its embedding.
     */
    Mono<Chunk> storeChunk(Chunk chunk);

    /**
     * Store multiple chunks (batch optimized).
     */
    Flux<Chunk> storeChunks(List<Chunk> chunks);

    /**
     * Find top-k most similar chunks to query vector.
     * 
     * @param queryEmbedding Query vector
     * @param topK           Number of results to return
     * @return Chunks ordered by similarity (descending)
     */
    Flux<Chunk> findSimilar(float[] queryEmbedding, int topK);

    /**
     * Hybrid search: vector similarity + metadata filtering.
     * 
     * Example: Find chunks similar to query AND linked to specific node label.
     */
    Flux<Chunk> findSimilarWithFilter(float[] queryEmbedding, int topK, ChunkFilter filter);

    /**
     * Retrieve chunk by ID.
     */
    Mono<Chunk> getChunk(UUID chunkId);

    /**
     * Delete chunk by ID.
     */
    Mono<Void> deleteChunk(UUID chunkId);

    /**
     * Delete all chunks linked to a node.
     */
    Mono<Void> deleteChunksByNodeId(UUID nodeId);

    /**
     * Get vector store statistics.
     */
    Mono<VectorStats> getStatistics();

    /**
     * Clear all vectors (use with caution).
     */
    Mono<Void> clear();

    /**
     * Filter for hybrid vector search.
     */
    class ChunkFilter {
        private UUID linkedNodeId;
        private String nodeLabel;

        public ChunkFilter() {
        }

        public UUID getLinkedNodeId() {
            return linkedNodeId;
        }

        public void setLinkedNodeId(UUID linkedNodeId) {
            this.linkedNodeId = linkedNodeId;
        }

        public String getNodeLabel() {
            return nodeLabel;
        }

        public void setNodeLabel(String nodeLabel) {
            this.nodeLabel = nodeLabel;
        }
    }
}
