package org.ddse.ml.cef.repository.duckdb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.ddse.ml.cef.graph.GraphStats;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.graph.GraphSubgraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure SQL implementation of GraphStore for DuckDB.
 * All graph traversals are performed using SQL queries - NO in-memory caching.
 * 
 * <p>This ensures consistent behavior with other SQL-based graph stores
 * (PgSqlGraphStore, PgAgeGraphStore) and enables true performance benchmarking.</p>
 * 
 * <h3>Performance Characteristics:</h3>
 * <ul>
 *   <li><b>1-2 hop traversals:</b> Excellent (simple JOINs)</li>
 *   <li><b>3+ hop traversals:</b> Acceptable (recursive CTEs)</li>
 *   <li><b>5+ hop traversals:</b> May be slow (consider Neo4j for complex traversals)</li>
 * </ul>
 * 
 * <p><b>Note:</b> This class is NOT a @Component. It is created by GraphStoreAutoConfiguration
 * when cef.graph.store=duckdb (or not specified, as DuckDB is the default).</p>
 * 
 * @author mrmanna
 * @since v0.6
 */
public class DuckDbGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(DuckDbGraphStore.class);
    private static final int DEFAULT_MAX_TRAVERSAL_DEPTH = 5;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredRelationTypes = new HashSet<>();
    private final int maxTraversalDepth;

    public DuckDbGraphStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.maxTraversalDepth = DEFAULT_MAX_TRAVERSAL_DEPTH;
        log.info("DuckDbGraphStore created - pure SQL implementation (no in-memory caching)");
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        return Mono.fromCallable(() -> {
            log.info("Initializing DuckDbGraphStore with {} relation types", relationTypes.size());
            for (RelationType rt : relationTypes) {
                registeredRelationTypes.add(rt.getName());
            }
            log.info("DuckDbGraphStore initialization complete - {} relation types registered",
                    registeredRelationTypes.size());
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Node> addNode(Node node) {
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

            jdbcTemplate.update(sql,
                    node.getId().toString(),
                    node.getLabel(),
                    serializeProperties(node.getProperties()),
                    node.getVectorizableContent(),
                    Timestamp.from(node.getCreated()),
                    Timestamp.from(node.getUpdated()),
                    node.getVersion() != null ? node.getVersion() : 0L);

            node.setNew(false);
            return node;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return Flux.fromIterable(nodes)
                .flatMap(this::addNode)
                .doOnComplete(() -> log.info("Batch added {} nodes to DuckDB", nodes.size()));
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
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

            jdbcTemplate.update(sql,
                    edge.getId().toString(),
                    edge.getRelationType(),
                    edge.getSourceNodeId().toString(),
                    edge.getTargetNodeId().toString(),
                    serializeProperties(edge.getProperties()),
                    edge.getWeight() != null ? edge.getWeight() : 1.0,
                    Timestamp.from(edge.getCreated()));

            edge.setNew(false);
            return edge;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return Flux.fromIterable(edges)
                .flatMap(this::addEdge)
                .doOnComplete(() -> log.info("Batch added {} edges to DuckDB", edges.size()));
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT id, label, vectorizable_content, properties, created, updated, version FROM nodes WHERE id = ?";
            List<Node> results = jdbcTemplate.query(sql, new NodeRowMapper(), nodeId.toString());
            return results.isEmpty() ? null : results.get(0);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT id, relation_type, source_node_id, target_node_id, properties, weight, created FROM edges WHERE id = ?";
            List<Edge> results = jdbcTemplate.query(sql, new EdgeRowMapper(), edgeId.toString());
            return results.isEmpty() ? null : results.get(0);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = "SELECT id, label, vectorizable_content, properties, created, updated, version FROM nodes WHERE label = ?";
            return jdbcTemplate.query(sql, new NodeRowMapper(), label);
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = "SELECT id, relation_type, source_node_id, target_node_id, properties, weight, created FROM edges WHERE relation_type = ?";
            return jdbcTemplate.query(sql, new EdgeRowMapper(), relationType);
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated, n.version
                FROM nodes n
                WHERE n.id IN (
                    SELECT target_node_id FROM edges WHERE source_node_id = ?
                    UNION
                    SELECT source_node_id FROM edges WHERE target_node_id = ?
                )
                """;
            return jdbcTemplate.query(sql, new NodeRowMapper(), nodeId.toString(), nodeId.toString());
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated, n.version
                FROM nodes n
                WHERE n.id IN (
                    SELECT target_node_id FROM edges WHERE source_node_id = ? AND relation_type = ?
                    UNION
                    SELECT source_node_id FROM edges WHERE target_node_id = ? AND relation_type = ?
                )
                """;
            return jdbcTemplate.query(sql, new NodeRowMapper(), 
                    nodeId.toString(), relationType, nodeId.toString(), relationType);
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.<List<UUID>>fromCallable(() -> {
            // DuckDB supports recursive CTEs
            String sql = """
                WITH RECURSIVE path_search AS (
                    SELECT 
                        source_node_id, 
                        target_node_id, 
                        [source_node_id, target_node_id] AS path, 
                        1 AS depth
                    FROM edges
                    WHERE source_node_id = ?
                    
                    UNION ALL
                    
                    SELECT 
                        e.source_node_id, 
                        e.target_node_id, 
                        list_append(p.path, e.target_node_id), 
                        p.depth + 1
                    FROM edges e
                    JOIN path_search p ON e.source_node_id = p.target_node_id
                    WHERE NOT list_contains(p.path, e.target_node_id)
                    AND p.depth < ?
                )
                SELECT path FROM path_search
                WHERE target_node_id = ?
                ORDER BY depth
                LIMIT 1
                """;

            List<List<UUID>> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                // DuckDB returns arrays differently - handle the path array
                Object pathObj = rs.getObject("path");
                List<UUID> pathList = new ArrayList<>();
                if (pathObj instanceof Object[]) {
                    for (Object id : (Object[]) pathObj) {
                        pathList.add(UUID.fromString(id.toString()));
                    }
                }
                return pathList;
            }, sourceId.toString(), maxTraversalDepth, targetId.toString());

            return results.isEmpty() ? new ArrayList<>() : results.get(0);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        int safeDepth = Math.min(depth, maxTraversalDepth);

        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                WITH RECURSIVE k_hop AS (
                    SELECT DISTINCT 
                        CASE WHEN source_node_id = ? THEN target_node_id ELSE source_node_id END AS node_id,
                        1 AS hop
                    FROM edges
                    WHERE source_node_id = ? OR target_node_id = ?
                    
                    UNION
                    
                    SELECT DISTINCT
                        CASE WHEN e.source_node_id = k.node_id THEN e.target_node_id ELSE e.source_node_id END AS node_id,
                        k.hop + 1
                    FROM edges e
                    JOIN k_hop k ON e.source_node_id = k.node_id OR e.target_node_id = k.node_id
                    WHERE k.hop < ?
                    AND CASE WHEN e.source_node_id = k.node_id THEN e.target_node_id ELSE e.source_node_id END != ?
                )
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated, n.version
                FROM nodes n
                JOIN k_hop k ON n.id = k.node_id
                WHERE n.id != ?
                """;

            String nodeIdStr = nodeId.toString();
            return jdbcTemplate.query(sql, new NodeRowMapper(),
                    nodeIdStr, nodeIdStr, nodeIdStr, safeDepth, nodeIdStr, nodeIdStr);
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        int safeDepth = Math.min(depth, maxTraversalDepth);

        return Mono.fromCallable(() -> {
            Set<UUID> visitedNodes = new HashSet<>(seedNodeIds);
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();

            // Get seed nodes
            for (UUID seedId : seedNodeIds) {
                Node node = getNodeDirect(seedId);
                if (node != null) {
                    nodes.add(node);
                }
            }

            // BFS traversal
            Set<UUID> currentLevel = new HashSet<>(seedNodeIds);

            for (int hop = 0; hop < safeDepth && !currentLevel.isEmpty(); hop++) {
                Set<UUID> nextLevel = new HashSet<>();

                for (UUID nodeId : currentLevel) {
                    List<Edge> nodeEdges = getEdgesForNodeDirect(nodeId);

                    for (Edge edge : nodeEdges) {
                        UUID neighborId = edge.getSourceNodeId().equals(nodeId)
                                ? edge.getTargetNodeId()
                                : edge.getSourceNodeId();

                        if (!visitedNodes.contains(neighborId)) {
                            visitedNodes.add(neighborId);
                            nextLevel.add(neighborId);

                            Node neighbor = getNodeDirect(neighborId);
                            if (neighbor != null) {
                                nodes.add(neighbor);
                            }
                        }
                    }
                }

                currentLevel = nextLevel;
            }

            // Get all edges between visited nodes
            edges = getEdgesBetweenNodes(visitedNodes);

            return new GraphSubgraph(nodes, edges);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Node getNodeDirect(UUID nodeId) {
        String sql = "SELECT id, label, vectorizable_content, properties, created, updated, version FROM nodes WHERE id = ?";
        List<Node> results = jdbcTemplate.query(sql, new NodeRowMapper(), nodeId.toString());
        return results.isEmpty() ? null : results.get(0);
    }

    private List<Edge> getEdgesForNodeDirect(UUID nodeId) {
        String sql = """
            SELECT id, relation_type, source_node_id, target_node_id, properties, weight, created 
            FROM edges 
            WHERE source_node_id = ? OR target_node_id = ?
            """;
        return jdbcTemplate.query(sql, new EdgeRowMapper(), nodeId.toString(), nodeId.toString());
    }

    private List<Edge> getEdgesBetweenNodes(Set<UUID> nodeIds) {
        if (nodeIds.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = nodeIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = String.format("""
            SELECT id, relation_type, source_node_id, target_node_id, properties, weight, created 
            FROM edges 
            WHERE source_node_id IN (%s) AND target_node_id IN (%s)
            """, placeholders, placeholders);

        List<Object> params = new ArrayList<>();
        nodeIds.forEach(id -> params.add(id.toString()));
        nodeIds.forEach(id -> params.add(id.toString()));

        return jdbcTemplate.query(sql, new EdgeRowMapper(), params.toArray());
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            // Delete edges first (foreign key constraint)
            jdbcTemplate.update("DELETE FROM edges WHERE source_node_id = ? OR target_node_id = ?",
                    nodeId.toString(), nodeId.toString());
            jdbcTemplate.update("DELETE FROM nodes WHERE id = ?", nodeId.toString());
            log.debug("Node {} and its edges deleted from DuckDB", nodeId);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            jdbcTemplate.update("DELETE FROM edges WHERE id = ?", edgeId.toString());
            log.debug("Edge {} deleted from DuckDB", edgeId);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromCallable(() -> {
            jdbcTemplate.update("DELETE FROM edges");
            jdbcTemplate.update("DELETE FROM nodes");
            log.info("Cleared all graph data from DuckDB");
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.fromCallable(() -> {
            Long nodeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM nodes", Long.class);
            Long edgeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edges", Long.class);

            Map<String, Long> nodesByLabel = new HashMap<>();
            jdbcTemplate.query("SELECT label, COUNT(*) as cnt FROM nodes GROUP BY label", rs -> {
                nodesByLabel.put(rs.getString("label"), rs.getLong("cnt"));
            });

            Map<String, Long> edgesByType = new HashMap<>();
            jdbcTemplate.query("SELECT relation_type, COUNT(*) as cnt FROM edges GROUP BY relation_type", rs -> {
                edgesByType.put(rs.getString("relation_type"), rs.getLong("cnt"));
            });

            long nodes = nodeCount != null ? nodeCount : 0L;
            long edges = edgeCount != null ? edgeCount : 0L;
            double avgDegree = nodes > 0 ? (double) edges / nodes : 0.0;

            return new GraphStats(nodes, edges, nodesByLabel, edgesByType, avgDegree);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> getEdgesForNode(UUID nodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                SELECT id, relation_type, source_node_id, target_node_id, properties, weight, created 
                FROM edges 
                WHERE source_node_id = ? OR target_node_id = ?
                """;
            return jdbcTemplate.query(sql, new EdgeRowMapper(), nodeId.toString(), nodeId.toString());
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    // ========== Helper Methods ==========

    private String serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize properties", e);
            return "{}";
        }
    }

    private Map<String, Object> deserializeProperties(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize properties", e);
            return new HashMap<>();
        }
    }

    // ========== Row Mappers ==========

    private class NodeRowMapper implements RowMapper<Node> {
        @Override
        public Node mapRow(ResultSet rs, int rowNum) throws SQLException {
            Node node = new Node();
            node.setId(UUID.fromString(rs.getString("id")));
            node.setLabel(rs.getString("label"));
            node.setVectorizableContent(rs.getString("vectorizable_content"));
            node.setProperties(deserializeProperties(rs.getString("properties")));

            Timestamp created = rs.getTimestamp("created");
            if (created != null) {
                node.setCreated(created.toInstant());
            }

            Timestamp updated = rs.getTimestamp("updated");
            if (updated != null) {
                node.setUpdated(updated.toInstant());
            }

            node.setVersion(rs.getLong("version"));
            node.setNew(false);
            return node;
        }
    }

    private class EdgeRowMapper implements RowMapper<Edge> {
        @Override
        public Edge mapRow(ResultSet rs, int rowNum) throws SQLException {
            Edge edge = new Edge();
            edge.setId(UUID.fromString(rs.getString("id")));
            edge.setRelationType(rs.getString("relation_type"));
            edge.setSourceNodeId(UUID.fromString(rs.getString("source_node_id")));
            edge.setTargetNodeId(UUID.fromString(rs.getString("target_node_id")));
            edge.setProperties(deserializeProperties(rs.getString("properties")));

            Double weight = rs.getDouble("weight");
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
