package org.ddse.ml.cef.service;

import org.ddse.ml.cef.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service interface for indexing knowledge graph elements.
 * Handles dual persistence: database + in-memory graph.
 * Generates embeddings for chunks and persists to vector store.
 */
public interface KnowledgeIndexer {

    /**
     * Initialize the indexer by loading existing data from database.
     * Should be called once at startup.
     * 
     * @return Mono with initialization statistics
     */
    Mono<IndexStats> initialize();

    /**
     * Index a single node.
     * Persists to database and adds to in-memory graph.
     * 
     * @param input Node input data
     * @return Mono with index result
     */
    Mono<IndexResult> indexNode(NodeInput input);

    /**
     * Index a single edge.
     * Persists to database and adds to in-memory graph.
     * Validates source and target nodes exist.
     * 
     * @param input Edge input data
     * @return Mono with index result
     */
    Mono<IndexResult> indexEdge(EdgeInput input);

    /**
     * Index a single chunk.
     * Generates embedding, persists to database.
     * If linkedNodeId provided, associates with node.
     * 
     * @param input Chunk input data
     * @return Mono with index result
     */
    Mono<IndexResult> indexChunk(ChunkInput input);

    /**
     * Index a batch of nodes, edges, and chunks.
     * Processes in order: nodes -> edges -> chunks.
     * Transactional: all succeed or all fail.
     * 
     * @param batch Batch input data
     * @return Mono with batch index result
     */
    Mono<BatchIndexResult> indexBatch(BatchInput batch);

    /**
     * Update an existing node.
     * Updates both database and in-memory graph.
     * 
     * @param nodeId Node ID to update
     * @param input  Updated node data
     * @return Mono with index result
     */
    Mono<IndexResult> updateNode(UUID nodeId, NodeInput input);

    /**
     * Update an existing edge.
     * Updates both database and in-memory graph.
     * 
     * @param edgeId Edge ID to update
     * @param input  Updated edge data
     * @return Mono with index result
     */
    Mono<IndexResult> updateEdge(UUID edgeId, EdgeInput input);

    /**
     * Update an existing chunk.
     * Regenerates embedding if content changed.
     * 
     * @param chunkId Chunk ID to update
     * @param input   Updated chunk data
     * @return Mono with index result
     */
    Mono<IndexResult> updateChunk(UUID chunkId, ChunkInput input);

    /**
     * Delete a node.
     * Cascades to all connected edges.
     * Removes from database and in-memory graph.
     * 
     * @param nodeId Node ID to delete
     * @return Mono with number of deleted items (node + edges)
     */
    Mono<Integer> deleteNode(UUID nodeId);

    /**
     * Delete an edge.
     * Removes from database and in-memory graph.
     * 
     * @param edgeId Edge ID to delete
     * @return Mono with boolean indicating success
     */
    Mono<Boolean> deleteEdge(UUID edgeId);

    /**
     * Delete a chunk.
     * Removes from database.
     * 
     * @param chunkId Chunk ID to delete
     * @return Mono with boolean indicating success
     */
    Mono<Boolean> deleteChunk(UUID chunkId);

    /**
     * Get current indexing statistics.
     * 
     * @return Mono with current stats
     */
    Mono<IndexStats> getStats();

    /**
     * Clear all indexed data.
     * WARNING: This removes all data from database and memory.
     * 
     * @return Mono signaling completion
     */
    Mono<Void> clearAll();
}
