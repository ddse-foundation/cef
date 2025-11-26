package org.ddse.ml.cef.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Node;
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
 * JDBC implementation of NodeRepository for DuckDB.
 * Wraps blocking JDBC calls in reactive types using
 * Mono.fromCallable().subscribeOn(Schedulers.boundedElastic()).
 */
@Component
@ConditionalOnProperty(name = "cef.database.type", havingValue = "duckdb", matchIfMissing = true)
public class DuckDbNodeRepository {

    private static final Logger log = LoggerFactory.getLogger(DuckDbNodeRepository.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DuckDbNodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Mono<Node> save(Node node) {
        return Mono.fromCallable(() -> {
            if (node.getId() == null) {
                node.setId(UUID.randomUUID());
            }
            if (node.getCreated() == null) {
                node.setCreated(Instant.now());
            }
            node.setUpdated(Instant.now());

            String sql = """
                    INSERT OR REPLACE INTO nodes (id, label, properties, vectorizable_content, created, updated, version)
                    VALUES (?, ?, ?::JSON, ?, ?, ?, ?)
                    """;

            String propertiesJson = "{}";
            if (node.getProperties() != null) {
                try {
                    propertiesJson = objectMapper.writeValueAsString(node.getProperties());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize properties", e);
                }
            }

            jdbcTemplate.update(sql,
                    node.getId(),
                    node.getLabel(),
                    propertiesJson,
                    node.getVectorizableContent(),
                    Timestamp.from(node.getCreated()),
                    Timestamp.from(node.getUpdated()),
                    node.getVersion() != null ? node.getVersion() : 0L);

            node.setNew(false);
            return node;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Node> findById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT * FROM nodes WHERE id = ?";
            return jdbcTemplate.query(sql, new NodeRowMapper(), id).stream().findFirst().orElse(null);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Node> findAll() {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM nodes";
            return Flux.fromIterable(jdbcTemplate.query(sql, new NodeRowMapper()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Node> findByLabel(String label) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM nodes WHERE label = ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new NodeRowMapper(), label));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Node> findByLabelPaginated(String label, int limit, int offset) {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM nodes WHERE label = ? ORDER BY created DESC LIMIT ? OFFSET ?";
            return Flux.fromIterable(jdbcTemplate.query(sql, new NodeRowMapper(), label, limit, offset));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Long> countByLabel(String label) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM nodes WHERE label = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, label);
            return count != null ? count : 0L;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<Node> findNodesWithVectorizableContent() {
        return Flux.defer(() -> {
            String sql = "SELECT * FROM nodes WHERE vectorizable_content IS NOT NULL";
            return Flux.fromIterable(jdbcTemplate.query(sql, new NodeRowMapper()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM nodes WHERE id = ?";
            jdbcTemplate.update(sql, id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteByLabel(String label) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM nodes WHERE label = ?";
            jdbcTemplate.update(sql, label);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteAll() {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM nodes";
            jdbcTemplate.update(sql);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Boolean> existsById(UUID id) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM nodes WHERE id = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
            return count != null && count > 0;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Long> count() {
        return Mono.fromCallable(() -> {
            String sql = "SELECT COUNT(*) FROM nodes";
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private class NodeRowMapper implements RowMapper<Node> {
        @Override
        public Node mapRow(ResultSet rs, int rowNum) throws SQLException {
            Node node = new Node();
            node.setId(UUID.fromString(rs.getString("id")));
            node.setLabel(rs.getString("label"));

            String propertiesJson = rs.getString("properties");
            if (propertiesJson != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> properties = objectMapper.readValue(propertiesJson, Map.class);
                    node.setProperties(properties);
                } catch (Exception e) {
                    log.error("Failed to deserialize properties", e);
                }
            }

            node.setVectorizableContent(rs.getString("vectorizable_content"));

            Timestamp created = rs.getTimestamp("created");
            if (created != null) {
                node.setCreated(created.toInstant());
            }

            Timestamp updated = rs.getTimestamp("updated");
            if (updated != null) {
                node.setUpdated(updated.toInstant());
            }

            long version = rs.getLong("version");
            node.setVersion(version);
            node.setNew(false);

            return node;
        }
    }
}
