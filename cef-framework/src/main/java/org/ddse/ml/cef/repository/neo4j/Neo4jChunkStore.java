package org.ddse.ml.cef.repository.neo4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * Neo4j implementation of ChunkStore for vector storage.
 * Uses Neo4j's native vector index (available since Neo4j 5.11).
 * 
 * <p>Chunks are stored as nodes with label :CefChunk and vectors
 * are indexed using Neo4j's vector index for cosine similarity search.</p>
 * 
 * <p><b>Note:</b> This class is NOT a @Component. It is created by ChunkStoreAutoConfiguration
 * when cef.vector.store=neo4j is set.</p>
 * 
 * @author mrmanna
 * @since v0.6
 */
public class Neo4jChunkStore implements ChunkStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jChunkStore.class);
    private static final String CHUNK_LABEL = "CefChunk";
    private static final String VECTOR_INDEX_NAME = "cef_chunk_embedding_index";
    
    private final Driver driver;
    private final ObjectMapper objectMapper;
    private final int embeddingDimension;

    public Neo4jChunkStore(Driver driver, CefProperties cefProperties) {
        this.driver = driver;
        this.objectMapper = new ObjectMapper();
        this.embeddingDimension = cefProperties != null 
                ? cefProperties.getVector().getDimension() 
                : 768;
        log.info("Neo4jChunkStore initialized with embedding dimension: {}", embeddingDimension);
        initializeSchema();
    }

    private void initializeSchema() {
        try (Session session = driver.session()) {
            // Create unique constraint on chunk ID
            session.executeWrite(tx -> {
                tx.run("""
                    CREATE CONSTRAINT cef_chunk_id IF NOT EXISTS
                    FOR (c:CefChunk) REQUIRE c.id IS UNIQUE
                    """);
                return null;
            });

            // Create index on linkedNodeId for fast lookups
            session.executeWrite(tx -> {
                tx.run("""
                    CREATE INDEX cef_chunk_linked_node IF NOT EXISTS
                    FOR (c:CefChunk) ON (c.linkedNodeId)
                    """);
                return null;
            });

            // Create vector index for similarity search (Neo4j 5.11+)
            session.executeWrite(tx -> {
                try {
                    tx.run(String.format("""
                        CREATE VECTOR INDEX %s IF NOT EXISTS
                        FOR (c:CefChunk) ON (c.embedding)
                        OPTIONS {indexConfig: {
                            `vector.dimensions`: %d,
                            `vector.similarity_function`: 'cosine'
                        }}
                        """, VECTOR_INDEX_NAME, embeddingDimension));
                } catch (Exception e) {
                    log.warn("Could not create vector index (Neo4j 5.11+ required): {}", e.getMessage());
                }
                return null;
            });

            log.info("Neo4jChunkStore schema initialized");
        } catch (Exception e) {
            log.error("Failed to initialize Neo4jChunkStore schema", e);
        }
    }

    @Override
    public Mono<Chunk> save(Chunk chunk) {
        return Mono.fromCallable(() -> {
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }

            try (Session session = driver.session()) {
                session.executeWrite(tx -> {
                    String cypher = """
                        MERGE (c:CefChunk {id: $id})
                        SET c.content = $content,
                            c.embedding = $embedding,
                            c.metadata = $metadata,
                            c.linkedNodeId = $linkedNodeId
                        RETURN c
                        """;

                    tx.run(cypher, Map.of(
                            "id", chunk.getId().toString(),
                            "content", chunk.getContent() != null ? chunk.getContent() : "",
                            "embedding", toDoubleList(chunk.getEmbedding()),
                            "metadata", serializeMetadata(chunk.getMetadata()),
                            "linkedNodeId", chunk.getLinkedNodeId() != null 
                                    ? chunk.getLinkedNodeId().toString() 
                                    : ""
                    ));
                    return null;
                });
            }

            log.debug("Saved chunk {} linked to node {}", chunk.getId(), chunk.getLinkedNodeId());
            return chunk;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Chunk> findTopKSimilar(float[] queryVector, int topK) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Chunk> results = new ArrayList<>();

            try (Session session = driver.session()) {
                // Use Neo4j vector index for similarity search
                Result result = session.run("""
                    CALL db.index.vector.queryNodes($indexName, $topK, $queryVector)
                    YIELD node, score
                    RETURN node, score
                    ORDER BY score DESC
                    """, Map.of(
                        "indexName", VECTOR_INDEX_NAME,
                        "topK", topK,
                        "queryVector", toDoubleList(queryVector)
                ));

                while (result.hasNext()) {
                    org.neo4j.driver.Record record = result.next();
                    Node node = record.get("node").asNode();
                    Chunk chunk = mapNodeToChunk(node);
                    results.add(chunk);
                }
            } catch (Exception e) {
                log.error("Vector search failed, falling back to brute force: {}", e.getMessage());
                results.addAll(bruteForceSearch(queryVector, topK, null));
            }

            return results;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Chunk> findTopKSimilarWithNodeLabel(float[] queryVector, String nodeLabel, int topK) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Chunk> results = new ArrayList<>();

            try (Session session = driver.session()) {
                // Vector search with node label filter via join
                Result result = session.run("""
                    CALL db.index.vector.queryNodes($indexName, $topK * 3, $queryVector)
                    YIELD node AS chunk, score
                    MATCH (n:CefNode {id: chunk.linkedNodeId})
                    WHERE n.label = $nodeLabel
                    RETURN chunk, score
                    ORDER BY score DESC
                    LIMIT $topK
                    """, Map.of(
                        "indexName", VECTOR_INDEX_NAME,
                        "topK", topK,
                        "queryVector", toDoubleList(queryVector),
                        "nodeLabel", nodeLabel
                ));

                while (result.hasNext()) {
                    org.neo4j.driver.Record record = result.next();
                    Node node = record.get("chunk").asNode();
                    Chunk chunk = mapNodeToChunk(node);
                    results.add(chunk);
                }
            } catch (Exception e) {
                log.error("Filtered vector search failed: {}", e.getMessage());
                results.addAll(bruteForceSearch(queryVector, topK, nodeLabel));
            }

            return results;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Chunk> results = new ArrayList<>();

            try (Session session = driver.session()) {
                Result result = session.run("""
                    MATCH (c:CefChunk {linkedNodeId: $linkedNodeId})
                    RETURN c
                    """, Map.of("linkedNodeId", linkedNodeId.toString()));

                while (result.hasNext()) {
                    org.neo4j.driver.Record record = result.next();
                    Node node = record.get("c").asNode();
                    results.add(mapNodeToChunk(node));
                }
            }

            return results;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<Long> countByLinkedNodeId(UUID nodeId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                Result result = session.run("""
                    MATCH (c:CefChunk {linkedNodeId: $linkedNodeId})
                    RETURN count(c) AS count
                    """, Map.of("linkedNodeId", nodeId.toString()));

                if (result.hasNext()) {
                    return result.next().get("count").asLong();
                }
                return 0L;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteByLinkedNodeId(UUID nodeId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                session.executeWrite(tx -> {
                    tx.run("""
                        MATCH (c:CefChunk {linkedNodeId: $linkedNodeId})
                        DELETE c
                        """, Map.of("linkedNodeId", nodeId.toString()));
                    return null;
                });
            }
            log.debug("Deleted chunks for node {}", nodeId);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session()) {
                session.executeWrite(tx -> {
                    tx.run("MATCH (c:CefChunk) DELETE c");
                    return null;
                });
            }
            log.info("Deleted all chunks from Neo4j");
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ========== Helper Methods ==========

    private List<Chunk> bruteForceSearch(float[] queryVector, int topK, String nodeLabel) {
        List<Chunk> results = new ArrayList<>();

        try (Session session = driver.session()) {
            String cypher = nodeLabel != null 
                ? """
                    MATCH (c:CefChunk)
                    MATCH (n:CefNode {id: c.linkedNodeId})
                    WHERE n.label = $nodeLabel
                    RETURN c, c.embedding AS embedding
                    """
                : """
                    MATCH (c:CefChunk)
                    RETURN c, c.embedding AS embedding
                    """;

            Map<String, Object> params = nodeLabel != null 
                ? Map.of("nodeLabel", nodeLabel) 
                : Map.of();

            Result result = session.run(cypher, params);

            List<ChunkWithScore> scored = new ArrayList<>();
            while (result.hasNext()) {
                org.neo4j.driver.Record record = result.next();
                Node node = record.get("c").asNode();
                List<Double> embedding = record.get("embedding").asList(Value::asDouble);

                float[] embeddingArray = toFloatArray(embedding);
                double similarity = cosineSimilarity(queryVector, embeddingArray);

                Chunk chunk = mapNodeToChunk(node);
                scored.add(new ChunkWithScore(chunk, similarity));
            }

            // Sort by similarity descending and take topK
            scored.sort((a, b) -> Double.compare(b.score, a.score));
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                results.add(scored.get(i).chunk);
            }
        }

        return results;
    }

    private record ChunkWithScore(Chunk chunk, double score) {}

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
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

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private Chunk mapNodeToChunk(Node node) {
        Chunk chunk = new Chunk();
        chunk.setId(UUID.fromString(node.get("id").asString()));
        chunk.setContent(node.get("content").asString(""));

        // Parse embedding
        try {
            List<Double> embeddingList = node.get("embedding").asList(Value::asDouble);
            chunk.setEmbedding(toFloatArray(embeddingList));
        } catch (Exception e) {
            log.debug("Could not parse embedding for chunk {}", chunk.getId());
        }

        // Parse metadata
        String metadataStr = node.get("metadata").asString("{}");
        chunk.setMetadata(deserializeMetadata(metadataStr));

        // Parse linkedNodeId
        String linkedNodeIdStr = node.get("linkedNodeId").asString("");
        if (!linkedNodeIdStr.isEmpty()) {
            try {
                chunk.setLinkedNodeId(UUID.fromString(linkedNodeIdStr));
            } catch (Exception e) {
                log.debug("Invalid linkedNodeId: {}", linkedNodeIdStr);
            }
        }

        return chunk;
    }

    private List<Double> toDoubleList(float[] array) {
        if (array == null) {
            return List.of();
        }
        List<Double> list = new ArrayList<>(array.length);
        for (float f : array) {
            list.add((double) f);
        }
        return list;
    }

    private float[] toFloatArray(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return new float[0];
        }
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }
        return array;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata", e);
            return "{}";
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize metadata", e);
            return new HashMap<>();
        }
    }
}
