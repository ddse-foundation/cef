package org.ddse.ml.cef.indexer;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface for indexing knowledge into the CEF framework.
 * 
 * Philosophy: Bulk indexing (heavy operation), frequent retrieval (optimized).
 * 
 * Typical usage:
 * 1. Application registers relation types via initialize()
 * 2. Application indexes nodes, edges, chunks in batches
 * 3. Framework auto-generates embeddings for vectorizable content
 * 4. MCP tool performs optimized hybrid retrieval
 *
 * @author mrmanna
 */
public interface KnowledgeIndexer {

    /**
     * Initialize indexer with domain-specific relation types.
     * Must be called before indexing any data.
     * 
     * Example (Medical Domain):
     * 
     * <pre>
     * List&lt;RelationType&gt; types = List.of(
     *         new RelationType("TREATS", "Doctor", "Patient", CAUSAL, true),
     *         new RelationType("PRESCRIBES", "Doctor", "Medication", CAUSAL, true),
     *         new RelationType("HAS_SYMPTOM", "Patient", "Symptom", ASSOCIATIVE, true));
     * indexer.initialize(types).block();
     * </pre>
     */
    Mono<Void> initialize(List<RelationType> relationTypes);

    /**
     * Index a single node.
     * If node has vectorizableContent, framework auto-generates embedding.
     */
    Mono<Node> indexNode(Node node);

    /**
     * Index nodes in batch (optimized for bulk loading).
     * Recommended for initial data loading.
     */
    Flux<Node> indexNodes(List<Node> nodes);

    /**
     * Index a single edge.
     * Validates against registered relation types.
     */
    Mono<Edge> indexEdge(Edge edge);

    /**
     * Index edges in batch (optimized for bulk loading).
     */
    Flux<Edge> indexEdges(List<Edge> edges);

    /**
     * Index a single text chunk.
     * Framework auto-generates embedding using configured LLM.
     */
    Mono<Chunk> indexChunk(Chunk chunk);

    /**
     * Index chunks in batch.
     * Framework generates embeddings in parallel batches.
     */
    Flux<Chunk> indexChunks(List<Chunk> chunks);

    /**
     * Auto-generate embeddings for nodes with vectorizableContent.
     * Scans all nodes, generates embeddings for those without.
     * Expensive operation - use sparingly.
     */
    Mono<Void> generateNodeEmbeddings();

    /**
     * Auto-generate embeddings for chunks without embeddings.
     * Scans all chunks, generates embeddings for those missing.
     */
    Mono<Void> generateChunkEmbeddings();

    /**
     * Get indexing statistics.
     */
    Mono<IndexStats> getStatistics();
}
