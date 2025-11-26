package org.ddse.ml.cef.indexer;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.repository.ChunkRepository;
import org.ddse.ml.cef.repository.ChunkStore;
import org.ddse.ml.cef.repository.EdgeRepository;
import org.ddse.ml.cef.repository.NodeRepository;
import org.ddse.ml.cef.storage.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Default implementation of KnowledgeIndexer.
 * 
 * Handles:
 * - Batch indexing of nodes, edges, chunks
 * - Auto-generation of embeddings via Spring AI
 * - Validation of relation types
 * - Coordination between GraphStore and repositories
 *
 * @author mrmanna
 */
@Service
@ConditionalOnBean(EmbeddingModel.class)
public class DefaultKnowledgeIndexer implements KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(DefaultKnowledgeIndexer.class);

    private final GraphStore graphStore;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final ChunkStore chunkStore;
    private final EmbeddingModel embeddingModel;

    public DefaultKnowledgeIndexer(GraphStore graphStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NodeRepository nodeRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EdgeRepository edgeRepository,
            ChunkStore chunkStore,
            EmbeddingModel embeddingModel) {
        this.graphStore = graphStore;
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.chunkStore = chunkStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        log.info("Initializing knowledge indexer with {} relation types", relationTypes.size());
        return graphStore.initialize(relationTypes);
    }

    @Override
    public Mono<Node> indexNode(Node node) {
        // Generate embedding if vectorizableContent exists
        if (node.getVectorizableContent() != null && !node.getVectorizableContent().isBlank()) {
            return generateEmbeddingForNode(node)
                    .flatMap(graphStore::addNode);
        }
        return graphStore.addNode(node);
    }

    @Override
    public Flux<Node> indexNodes(List<Node> nodes) {
        log.info("Batch indexing {} nodes", nodes.size());

        // Separate nodes with/without vectorizable content
        List<Node> nodesWithContent = nodes.stream()
                .filter(n -> n.getVectorizableContent() != null && !n.getVectorizableContent().isBlank())
                .toList();

        List<Node> nodesWithoutContent = nodes.stream()
                .filter(n -> n.getVectorizableContent() == null || n.getVectorizableContent().isBlank())
                .toList();

        // Generate embeddings in parallel batches (10 at a time to avoid rate limits)
        Flux<Node> nodesWithEmbeddings = Flux.fromIterable(nodesWithContent)
                .flatMap(this::generateEmbeddingForNode, 10);

        Flux<Node> allNodes = Flux.concat(
                nodesWithEmbeddings,
                Flux.fromIterable(nodesWithoutContent));

        return allNodes.collectList()
                .flatMapMany(graphStore::batchAddNodes);
    }

    @Override
    public Mono<Edge> indexEdge(Edge edge) {
        return graphStore.addEdge(edge);
    }

    @Override
    public Flux<Edge> indexEdges(List<Edge> edges) {
        log.info("Batch indexing {} edges", edges.size());
        return graphStore.batchAddEdges(edges);
    }

    @Override
    public Mono<Chunk> indexChunk(Chunk chunk) {
        // Always generate embedding for chunks
        return generateEmbeddingForChunk(chunk)
                .flatMap(chunkStore::save);
    }

    @Override
    public Flux<Chunk> indexChunks(List<Chunk> chunks) {
        log.info("Batch indexing {} chunks", chunks.size());

        // Generate embeddings in parallel batches (20 at a time)
        return Flux.fromIterable(chunks)
                .flatMap(this::generateEmbeddingForChunk, 20)
                .flatMap(chunkStore::save);
    }

    @Override
    public Mono<Void> generateNodeEmbeddings() {
        log.info("Generating embeddings for nodes with vectorizable content");

        return nodeRepository.findNodesWithVectorizableContent()
                .filter(node -> node.getVectorizableContent() != null && !node.getVectorizableContent().isBlank())
                .flatMap(this::generateEmbeddingForNode, 10)
                .flatMap(nodeRepository::save)
                .then()
                .doOnSuccess(v -> log.info("Completed node embedding generation"));
    }

    @Override
    public Mono<Void> generateChunkEmbeddings() {
        log.info("Generating embeddings for chunks without embeddings");

        // ChunkStore doesn't have findChunksWithoutEmbeddings - skip for now
        // This is an optimization feature, not core functionality
        return Mono.empty();
    }

    @Override
    public Mono<IndexStats> getStatistics() {
        // When using DuckDB, repositories are optional (graphStore handles everything)
        if (nodeRepository == null || edgeRepository == null) {
            return graphStore.getStatistics()
                    .map(graphStats -> new IndexStats(
                            graphStats.getNodeCount(),
                            graphStats.getEdgeCount(),
                            0L, // chunk count not available from graphStore
                            0L, // nodes with content not tracked
                            0L // chunks with embeddings not tracked
                    ));
        }

        return Mono.zip(
                nodeRepository.count(),
                edgeRepository.count(),
                Mono.just(0L), // chunkStore doesn't have count()
                nodeRepository.findNodesWithVectorizableContent().count(),
                Mono.just(0L)) // chunkStore doesn't have findAll()
                .map(tuple -> new IndexStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5()));
    }

    /**
     * Generate embedding for a node's vectorizable content.
     * Stores embedding as separate chunk linked to the node.
     */
    private Mono<Node> generateEmbeddingForNode(Node node) {
        return Mono.fromCallable(() -> {
            float[] embedding = embeddingModel.embed(node.getVectorizableContent());

            // Create a chunk for the node's content
            Chunk chunk = new Chunk();
            chunk.setContent(node.getVectorizableContent());
            chunk.setEmbedding(embedding);
            chunk.setLinkedNodeId(node.getId());

            return chunk;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(chunkStore::save)
                .thenReturn(node)
                // .doOnSuccess(n -> log.debug("Generated embedding for node: {}", n.getId()))
                .onErrorResume(e -> {
                    log.error("Failed to generate embedding for node: {}", node.getId(), e);
                    return Mono.just(node); // Continue without embedding
                });
    }

    /**
     * Generate embedding for a chunk's content.
     */
    private Mono<Chunk> generateEmbeddingForChunk(Chunk chunk) {
        if (chunk.getContent() == null || chunk.getContent().isBlank()) {
            log.warn("Chunk {} has no content, skipping embedding generation", chunk.getId());
            return Mono.just(chunk);
        }

        return Mono.fromCallable(() -> {
            float[] embedding = embeddingModel.embed(chunk.getContent());
            chunk.setEmbedding(embedding);
            return chunk;
        })
                .subscribeOn(Schedulers.boundedElastic())
                // .doOnSuccess(c -> log.debug("Generated embedding for chunk: {}", c.getId()))
                .onErrorResume(e -> {
                    log.error("Failed to generate embedding for chunk: {}", chunk.getId(), e);
                    return Mono.just(chunk); // Continue without embedding
                });
    }
}
