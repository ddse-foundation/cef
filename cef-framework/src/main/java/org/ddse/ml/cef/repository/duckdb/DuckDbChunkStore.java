package org.ddse.ml.cef.repository.duckdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of ChunkStore for DuckDB.
 * Wraps blocking JDBC calls in reactive types.
 */
@Component
@ConditionalOnProperty(name = "cef.database.type", havingValue = "duckdb", matchIfMissing = true)
public class DuckDbChunkStore implements ChunkStore {

    private static final Logger log = LoggerFactory.getLogger(DuckDbChunkStore.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int embeddingDimension;

    public DuckDbChunkStore(JdbcTemplate jdbcTemplate, CefProperties cefProperties) {
        this.jdbcTemplate = jdbcTemplate;
        int configuredDimension = cefProperties != null
                ? cefProperties.getVector().getDimension()
                : 768;
        this.embeddingDimension = configuredDimension > 0 ? configuredDimension : 768;
        // Log database URL to confirm which DB file we're using
        try {
            String url = jdbcTemplate.getDataSource().getConnection().getMetaData().getURL();
            log.info("DuckDbChunkStore initialized with database URL: {}", url);
        } catch (Exception e) {
            log.warn("Could not retrieve database URL: {}", e.getMessage());
        }
        log.info("Using embedding vector dimension: {}", this.embeddingDimension);
        initializeSchema();
    }

    private void initializeSchema() {
        try {
            // Ensure vector extension is loaded (if available)
            // Note: In some DuckDB versions/environments, extensions need explicit loading
            jdbcTemplate.execute("INSTALL vss; LOAD vss;");
            jdbcTemplate.execute("INSTALL json; LOAD json;");

            // Create table if not exists
            String createChunksSql = String.format(
                    """
                                CREATE TABLE IF NOT EXISTS chunks (
                                    id UUID PRIMARY KEY,
                                    content TEXT,
                                    embedding FLOAT[%d],
                                    metadata JSON,
                                    linked_node_id UUID
                                )
                            """,
                    embeddingDimension);
            jdbcTemplate.execute(createChunksSql);

            // Create macro for cosine similarity if not exists (for older DuckDB versions
            // or if vss not loaded)
            // Modern DuckDB has array_cosine_similarity

        } catch (Exception e) {
            log.warn("Failed to initialize DuckDB schema: {}", e.getMessage());
        }
    }

    @Override
    public Mono<Chunk> save(Chunk chunk) {
        return Mono.fromCallable(() -> {
            if (chunk.getId() == null) {
                chunk.setId(UUID.randomUUID());
            }

            String sql = "INSERT OR REPLACE INTO chunks (id, content, embedding, metadata, linked_node_id) VALUES (?, ?, ?::FLOAT[], ?::JSON, ?)";

            // Convert embedding to String representation for DuckDB
            // "[1.0, 2.0, 3.0]"
            String embeddingString = toEmbeddingString(chunk.getEmbedding());

            String metadataJson = "{}";
            if (chunk.getMetadata() != null) {
                try {
                    metadataJson = objectMapper.writeValueAsString(chunk.getMetadata());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize metadata", e);
                }
            }

            // Convert UUIDs to strings explicitly for DuckDB
            jdbcTemplate.update(sql,
                    chunk.getId().toString(),
                    chunk.getContent(),
                    embeddingString,
                    metadataJson,
                    chunk.getLinkedNodeId().toString());
            chunk.setNew(false);
            log.debug("Saved chunk with id={}, linkedNodeId={}", chunk.getId(), chunk.getLinkedNodeId());
            return chunk;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Chunk> findTopKSimilar(float[] queryVector, int topK) {
        return Flux.defer(() -> {
            // Log what chunks exist before searching
            String countSql = "SELECT COUNT(*) FROM chunks";
            Long totalChunks = jdbcTemplate.queryForObject(countSql, Long.class);
            log.info("findTopKSimilar: Total chunks in DB: {}", totalChunks);

            if (totalChunks != null && totalChunks > 0) {
                String sampleSql = "SELECT id, linked_node_id FROM chunks LIMIT 3";
                List<Map<String, Object>> sampleChunks = jdbcTemplate.queryForList(sampleSql);
                log.info("Sample chunks in DB: {}", sampleChunks);
            }

            String sql = String.format(
                    """
                                SELECT *, array_cosine_similarity(embedding, cast(? as FLOAT[%d])) AS similarity
                                FROM chunks
                                WHERE embedding IS NOT NULL
                                ORDER BY similarity DESC
                                LIMIT ?
                            """,
                    embeddingDimension);

            // We need to pass the vector as a SQL array string or rely on PreparedStatement
            // DuckDB JDBC might require explicit casting or string formatting for arrays in
            // some versions
            // Let's try passing the array directly first.

            String queryVectorString = toEmbeddingString(queryVector);
            List<Chunk> chunks = jdbcTemplate.query(sql, new ChunkRowMapper(), queryVectorString, topK);
            return Flux.fromIterable(chunks);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Chunk> findTopKSimilarWithNodeLabel(float[] queryVector, String nodeLabel, int topK) {
        return Flux.defer(() -> {
            String sql = String.format(
                    """
                                SELECT c.*, array_cosine_similarity(c.embedding, cast(? as FLOAT[%d])) AS similarity
                                FROM chunks c
                                JOIN nodes n ON c.linked_node_id = n.id
                                WHERE n.label = ? AND c.embedding IS NOT NULL
                                ORDER BY similarity DESC
                                LIMIT ?
                            """,
                    embeddingDimension);

            String queryVectorString = toEmbeddingString(queryVector);
            List<Chunk> chunks = jdbcTemplate.query(sql, new ChunkRowMapper(), queryVectorString, nodeLabel, topK);
            return Flux.fromIterable(chunks);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Chunk> findByLinkedNodeId(UUID linkedNodeId) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM chunks WHERE linked_node_id = ?";
            List<Chunk> chunks = jdbcTemplate.query(sql, new ChunkRowMapper(), linkedNodeId.toString());
            return Flux.fromIterable(chunks);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> countByLinkedNodeId(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM chunks WHERE linked_node_id = ?";
            return jdbcTemplate.queryForObject(sql, Long.class, nodeId.toString());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteByLinkedNodeId(UUID nodeId) {
        return Mono.fromRunnable(() -> {
            String sql = "DELETE FROM chunks WHERE linked_node_id = ?";
            jdbcTemplate.update(sql, nodeId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteAll() {
        return Mono.fromCallable(() -> {
            int rows = jdbcTemplate.update("DELETE FROM chunks");
            log.info("Deleted {} rows from chunks table", rows);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private class ChunkRowMapper implements RowMapper<Chunk> {
        @Override
        public Chunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(rs.getString("id")));
            chunk.setContent(rs.getString("content"));

            // Handle embedding array
            // DuckDB returns arrays as java.sql.Array or specific types
            try {
                java.sql.Array array = rs.getArray("embedding");
                if (array != null) {
                    Object[] objArray = (Object[]) array.getArray();
                    float[] floats = new float[objArray.length];
                    for (int i = 0; i < objArray.length; i++) {
                        if (objArray[i] instanceof Number) {
                            floats[i] = ((Number) objArray[i]).floatValue();
                        }
                    }
                    chunk.setEmbedding(floats);
                }
            } catch (Exception e) {
                log.warn("Failed to map embedding: {}", e.getMessage());
            }

            String linkedNodeIdStr = rs.getString("linked_node_id");
            if (linkedNodeIdStr != null) {
                chunk.setLinkedNodeId(UUID.fromString(linkedNodeIdStr));
            }

            // Handle Metadata JSON
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);
                    chunk.setMetadata(metadata);
                } catch (Exception e) {
                    log.warn("Failed to parse metadata JSON", e);
                }
            }

            return chunk;
        }
    }

    private String toEmbeddingString(float[] vector) {
        if (vector == null)
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
