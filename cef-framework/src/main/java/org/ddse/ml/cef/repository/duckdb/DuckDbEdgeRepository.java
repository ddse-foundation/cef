package org.ddse.ml.cef.repository.duckdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Edge;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC implementation of EdgeRepository for DuckDB.
 * Wraps blocking JDBC calls in reactive types using
 * Mono.fromCallable().subscribeOn(Schedulers.boundedElastic()).
 */
@Component
@ConditionalOnProperty(name = "cef.database.type", havingValue = "duckdb", matchIfMissing = true)
public class DuckDbEdgeRepository {

    private static final Logger log = LoggerFactory.getLogger(DuckDbEdgeRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DuckDbEdgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Mono<Edge> save(Edge edge) {
        return Mono.fromCallable(() -> {
            if (edge.getId() == null) {
                edge.setId(UUID.randomUUID());
            }
            if (edge.getCreated() == null) {
                edge.setCreated(Instant.now());
            }

            String sql = """
                    INSERT OR REPLACE INTO edges (id, relation_type, source_node_id, target_node_id, properties, weight, created)
                    VALUES (?, ?, ?, ?, ?::JSON, ?, ?)
                    """;

            String propertiesJson = "{}";
            if (edge.getProperties() != null) {
                try {
                    propertiesJson = objectMapper.writeValueAsString(edge.getProperties());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize properties", e);
                }
            }

            jdbcTemplate.update(sql,
                    edge.getId(),
                    edge.getRelationType(),
                    edge.getSourceNodeId(),
                    edge.getTargetNodeId(),
                    propertiesJson,
                    edge.getWeight(),
                    Timestamp.from(edge.getCreated()));

            edge.setNew(false);
            return edge;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Edge> findById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT * FROM edges WHERE id = ?";
            return jdbcTemplate.query(sql, new EdgeRowMapper(), id).stream().findFirst().orElse(null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findAll() {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findByRelationType(String relationType) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges WHERE relation_type = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper(), relationType));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findBySourceNodeId(UUID sourceNodeId) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges WHERE source_node_id = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper(), sourceNodeId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findByTargetNodeId(UUID targetNodeId) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges WHERE target_node_id = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper(), targetNodeId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findByNodeId(UUID nodeId) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges WHERE source_node_id = ? OR target_node_id = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper(), nodeId, nodeId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Edge> findBySourceNodeIdAndRelationType(UUID sourceNodeId, String relationType) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM edges WHERE source_node_id = ? AND relation_type = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new EdgeRowMapper(), sourceNodeId, relationType));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> existsBySourceAndTargetAndType(UUID sourceNodeId, UUID targetNodeId, String relationType) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM edges WHERE source_node_id = ? AND target_node_id = ? AND relation_type = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, sourceNodeId, targetNodeId, relationType);
            return count != null && count > 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM edges WHERE id = ?";
            jdbcTemplate.update(sql, id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteByRelationType(String relationType) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM edges WHERE relation_type = ?";
            jdbcTemplate.update(sql, relationType);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteByNodeId(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM edges WHERE source_node_id = ? OR target_node_id = ?";
            jdbcTemplate.update(sql, nodeId, nodeId);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteAll() {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM edges";
            jdbcTemplate.update(sql);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Boolean> existsById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM edges WHERE id = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
            return count != null && count > 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Long> count() {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM edges";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private class EdgeRowMapper implements RowMapper<Edge> {
        @Override
        public Edge mapRow(ResultSet rs, int rowNum) throws SQLException {
            Edge edge = new Edge();
            edge.setId(UUID.fromString(rs.getString("id")));
            edge.setRelationType(rs.getString("relation_type"));
            edge.setSourceNodeId(UUID.fromString(rs.getString("source_node_id")));
            edge.setTargetNodeId(UUID.fromString(rs.getString("target_node_id")));

            String propertiesJson = rs.getString("properties");
            if (propertiesJson != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> properties = objectMapper.readValue(propertiesJson, Map.class);
                    edge.setProperties(properties);
                } catch (Exception e) {
                    log.error("Failed to deserialize properties", e);
                }
            }

            double weight = rs.getDouble("weight");
            if (!rs.wasNull()) {
                edge.setWeight(weight);
            }

            Timestamp created = rs.getTimestamp("created");
            if (created != null) {
                edge.setCreated(created.toInstant());
            }

            edge.setNew(false);

            return edge;
        }
    }
}
