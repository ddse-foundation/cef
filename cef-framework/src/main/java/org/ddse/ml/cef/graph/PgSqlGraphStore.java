package org.ddse.ml.cef.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * PostgreSQL-based GraphStore implementation using pure SQL adjacency tables.
 * 
 * <p>This implementation uses two tables:</p>
 * <ul>
 *   <li><b>cef_nodes</b> - Stores graph nodes with JSON properties</li>
 *   <li><b>cef_edges</b> - Stores edges as (source_id, target_id, type) with JSON properties</li>
 * </ul>
 * 
 * <h3>Performance Characteristics:</h3>
 * <ul>
 *   <li><b>1-2 hop traversals:</b> Excellent (simple JOINs)</li>
 *   <li><b>3+ hop traversals:</b> Acceptable (recursive CTEs)</li>
 *   <li><b>5+ hop traversals:</b> Slow (use Neo4j or pg-age instead)</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
public class PgSqlGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(PgSqlGraphStore.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Set<String> registeredRelationTypes = new HashSet<>();
    private final int maxTraversalDepth;

    public PgSqlGraphStore(DataSource dataSource, int maxTraversalDepth) {
        this.dataSource = dataSource;
        this.maxTraversalDepth = maxTraversalDepth;
        this.objectMapper = new ObjectMapper();
        log.info("PgSqlGraphStore created - pure SQL adjacency table implementation");
    }

    public PgSqlGraphStore(DataSource dataSource) {
        this(dataSource, 5);
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        return Mono.fromCallable(() -> {
            log.info("Initializing PgSqlGraphStore with {} relation types", relationTypes.size());
            
            try (Connection conn = dataSource.getConnection()) {
                createSchema(conn);
                
                for (RelationType rt : relationTypes) {
                    registeredRelationTypes.add(rt.getName());
                }
                
                log.info("PgSqlGraphStore initialization complete - {} relation types registered", 
                    registeredRelationTypes.size());
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void createSchema(Connection conn) throws SQLException {
        String createNodesTable = """
            CREATE TABLE IF NOT EXISTS cef_nodes (
                id UUID PRIMARY KEY,
                label VARCHAR(255) NOT NULL,
                vectorizable_content TEXT,
                properties JSONB DEFAULT '{}',
                created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        String createEdgesTable = """
            CREATE TABLE IF NOT EXISTS cef_edges (
                id UUID PRIMARY KEY,
                source_node_id UUID NOT NULL REFERENCES cef_nodes(id) ON DELETE CASCADE,
                target_node_id UUID NOT NULL REFERENCES cef_nodes(id) ON DELETE CASCADE,
                relation_type VARCHAR(255) NOT NULL,
                weight DOUBLE PRECISION DEFAULT 1.0,
                properties JSONB DEFAULT '{}',
                created TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createNodesTable);
            stmt.execute(createEdgesTable);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cef_nodes_label ON cef_nodes(label)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cef_edges_source ON cef_edges(source_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cef_edges_target ON cef_edges(target_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cef_edges_relation ON cef_edges(relation_type)");
            log.debug("Schema created/verified successfully");
        }
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return Mono.fromCallable(() -> {
            String sql = """
                INSERT INTO cef_nodes (id, label, vectorizable_content, properties, created, updated)
                VALUES (?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    label = EXCLUDED.label,
                    vectorizable_content = EXCLUDED.vectorizable_content,
                    properties = EXCLUDED.properties,
                    updated = EXCLUDED.updated
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, node.getId());
                ps.setString(2, node.getLabel());
                ps.setString(3, node.getVectorizableContent());
                ps.setString(4, serializeProperties(node.getProperties()));
                ps.setTimestamp(5, Timestamp.from(node.getCreated() != null ? node.getCreated() : Instant.now()));
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
                
                ps.executeUpdate();
                return node;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return Mono.fromCallable(() -> {
            if (!registeredRelationTypes.isEmpty() && 
                !registeredRelationTypes.contains(edge.getRelationType())) {
                throw new IllegalArgumentException("Unknown relation type: " + edge.getRelationType());
            }
            
            String sql = """
                INSERT INTO cef_edges (id, source_node_id, target_node_id, relation_type, weight, properties, created)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (id) DO UPDATE SET
                    source_node_id = EXCLUDED.source_node_id,
                    target_node_id = EXCLUDED.target_node_id,
                    relation_type = EXCLUDED.relation_type,
                    weight = EXCLUDED.weight,
                    properties = EXCLUDED.properties
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, edge.getId());
                ps.setObject(2, edge.getSourceNodeId());
                ps.setObject(3, edge.getTargetNodeId());
                ps.setString(4, edge.getRelationType());
                ps.setDouble(5, edge.getWeight() != null ? edge.getWeight() : 1.0);
                ps.setString(6, serializeProperties(edge.getProperties()));
                ps.setTimestamp(7, Timestamp.from(edge.getCreated() != null ? edge.getCreated() : Instant.now()));
                
                ps.executeUpdate();
                return edge;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT id, label, vectorizable_content, properties, created, updated FROM cef_nodes WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, nodeId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapNode(rs);
                    }
                    return null;
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            String sql = "SELECT id, source_node_id, target_node_id, relation_type, weight, properties, created FROM cef_edges WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, edgeId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapEdge(rs);
                    }
                    return null;
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Node> nodes = new ArrayList<>();
            String sql = "SELECT id, label, vectorizable_content, properties, created, updated FROM cef_nodes WHERE label = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, label);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        nodes.add(mapNode(rs));
                    }
                }
            }
            return nodes;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Edge> edges = new ArrayList<>();
            String sql = "SELECT id, source_node_id, target_node_id, relation_type, weight, properties, created FROM cef_edges WHERE relation_type = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, relationType);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        edges.add(mapEdge(rs));
                    }
                }
            }
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Node> neighbors = new ArrayList<>();
            String sql = """
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated
                FROM cef_nodes n
                WHERE n.id IN (
                    SELECT target_node_id FROM cef_edges WHERE source_node_id = ?
                    UNION
                    SELECT source_node_id FROM cef_edges WHERE target_node_id = ?
                )
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, nodeId);
                ps.setObject(2, nodeId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        neighbors.add(mapNode(rs));
                    }
                }
            }
            return neighbors;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Node> neighbors = new ArrayList<>();
            String sql = """
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated
                FROM cef_nodes n
                WHERE n.id IN (
                    SELECT target_node_id FROM cef_edges WHERE source_node_id = ? AND relation_type = ?
                    UNION
                    SELECT source_node_id FROM cef_edges WHERE target_node_id = ? AND relation_type = ?
                )
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, nodeId);
                ps.setString(2, relationType);
                ps.setObject(3, nodeId);
                ps.setString(4, relationType);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        neighbors.add(mapNode(rs));
                    }
                }
            }
            return neighbors;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.<List<UUID>>fromCallable(() -> {
            String sql = """
                WITH RECURSIVE path_search AS (
                    SELECT source_node_id, target_node_id, ARRAY[source_node_id, target_node_id] AS path, 1 AS depth
                    FROM cef_edges
                    WHERE source_node_id = ?
                    
                    UNION ALL
                    
                    SELECT e.source_node_id, e.target_node_id, p.path || e.target_node_id, p.depth + 1
                    FROM cef_edges e
                    JOIN path_search p ON e.source_node_id = p.target_node_id
                    WHERE NOT e.target_node_id = ANY(p.path)
                    AND p.depth < ?
                )
                SELECT path FROM path_search
                WHERE target_node_id = ?
                ORDER BY depth
                LIMIT 1
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, sourceId);
                ps.setInt(2, maxTraversalDepth);
                ps.setObject(3, targetId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID[] pathArray = (UUID[]) rs.getArray("path").getArray();
                        return new ArrayList<UUID>(Arrays.asList(pathArray));
                    }
                    return new ArrayList<UUID>();
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        int safeDepth = Math.min(depth, maxTraversalDepth);
        
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Node> neighbors = new ArrayList<>();
            
            String sql = """
                WITH RECURSIVE k_hop AS (
                    SELECT DISTINCT 
                        CASE WHEN source_node_id = ? THEN target_node_id ELSE source_node_id END AS node_id,
                        1 AS hop
                    FROM cef_edges
                    WHERE source_node_id = ? OR target_node_id = ?
                    
                    UNION
                    
                    SELECT DISTINCT
                        CASE WHEN e.source_node_id = k.node_id THEN e.target_node_id ELSE e.source_node_id END AS node_id,
                        k.hop + 1
                    FROM cef_edges e
                    JOIN k_hop k ON e.source_node_id = k.node_id OR e.target_node_id = k.node_id
                    WHERE k.hop < ?
                    AND CASE WHEN e.source_node_id = k.node_id THEN e.target_node_id ELSE e.source_node_id END != ?
                )
                SELECT DISTINCT n.id, n.label, n.vectorizable_content, n.properties, n.created, n.updated
                FROM cef_nodes n
                JOIN k_hop k ON n.id = k.node_id
                WHERE n.id != ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, nodeId);
                ps.setObject(2, nodeId);
                ps.setObject(3, nodeId);
                ps.setInt(4, safeDepth);
                ps.setObject(5, nodeId);
                ps.setObject(6, nodeId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        neighbors.add(mapNode(rs));
                    }
                }
            }
            return neighbors;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        int safeDepth = Math.min(depth, maxTraversalDepth);
        
        return Mono.fromCallable(() -> {
            Set<UUID> visitedNodes = new HashSet<>(seedNodeIds);
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            
            try (Connection conn = dataSource.getConnection()) {
                for (UUID seedId : seedNodeIds) {
                    Node node = getNodeDirect(conn, seedId);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
                
                Set<UUID> currentLevel = new HashSet<>(seedNodeIds);
                
                for (int hop = 0; hop < safeDepth && !currentLevel.isEmpty(); hop++) {
                    Set<UUID> nextLevel = new HashSet<>();
                    
                    for (UUID nodeId : currentLevel) {
                        List<Edge> nodeEdges = getEdgesForNode(conn, nodeId);
                        
                        for (Edge edge : nodeEdges) {
                            UUID neighborId = edge.getSourceNodeId().equals(nodeId) 
                                ? edge.getTargetNodeId() 
                                : edge.getSourceNodeId();
                            
                            if (!visitedNodes.contains(neighborId)) {
                                visitedNodes.add(neighborId);
                                nextLevel.add(neighborId);
                                
                                Node neighbor = getNodeDirect(conn, neighborId);
                                if (neighbor != null) {
                                    nodes.add(neighbor);
                                }
                            }
                        }
                    }
                    
                    currentLevel = nextLevel;
                }
                
                edges = getEdgesBetweenNodes(conn, visitedNodes);
            }
            
            return new GraphSubgraph(nodes, edges);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Node getNodeDirect(Connection conn, UUID nodeId) throws SQLException {
        String sql = "SELECT id, label, vectorizable_content, properties, created, updated FROM cef_nodes WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapNode(rs);
                }
                return null;
            }
        }
    }

    private List<Edge> getEdgesForNode(Connection conn, UUID nodeId) throws SQLException {
        List<Edge> edges = new ArrayList<>();
        String sql = """
            SELECT id, source_node_id, target_node_id, relation_type, weight, properties, created 
            FROM cef_edges 
            WHERE source_node_id = ? OR target_node_id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, nodeId);
            ps.setObject(2, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    edges.add(mapEdge(rs));
                }
            }
        }
        return edges;
    }

    private List<Edge> getEdgesBetweenNodes(Connection conn, Set<UUID> nodeIds) throws SQLException {
        if (nodeIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Edge> edges = new ArrayList<>();
        
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < nodeIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = String.format("""
            SELECT id, source_node_id, target_node_id, relation_type, weight, properties, created 
            FROM cef_edges 
            WHERE source_node_id IN (%s) AND target_node_id IN (%s)
            """, placeholders, placeholders);
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (UUID id : nodeIds) {
                ps.setObject(idx++, id);
            }
            for (UUID id : nodeIds) {
                ps.setObject(idx++, id);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    edges.add(mapEdge(rs));
                }
            }
        }
        return edges;
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM cef_nodes WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, nodeId);
                ps.executeUpdate();
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            String sql = "DELETE FROM cef_edges WHERE id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setObject(1, edgeId);
                ps.executeUpdate();
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute("TRUNCATE TABLE cef_edges CASCADE");
                stmt.execute("TRUNCATE TABLE cef_nodes CASCADE");
                log.info("Cleared all graph data from PostgreSQL");
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                long nodeCount = 0;
                long edgeCount = 0;
                Map<String, Long> nodesByLabel = new HashMap<>();
                Map<String, Long> edgesByType = new HashMap<>();
                
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM cef_nodes")) {
                    if (rs.next()) {
                        nodeCount = rs.getLong(1);
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM cef_edges")) {
                    if (rs.next()) {
                        edgeCount = rs.getLong(1);
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT label, COUNT(*) as cnt FROM cef_nodes GROUP BY label")) {
                    while (rs.next()) {
                        nodesByLabel.put(rs.getString("label"), rs.getLong("cnt"));
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT relation_type, COUNT(*) as cnt FROM cef_edges GROUP BY relation_type")) {
                    while (rs.next()) {
                        edgesByType.put(rs.getString("relation_type"), rs.getLong("cnt"));
                    }
                }
                
                double avgDegree = nodeCount > 0 ? (2.0 * edgeCount) / nodeCount : 0.0;
                
                return new GraphStats(nodeCount, edgeCount, nodesByLabel, edgesByType, avgDegree);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                INSERT INTO cef_nodes (id, label, vectorizable_content, properties, created, updated)
                VALUES (?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    label = EXCLUDED.label,
                    vectorizable_content = EXCLUDED.vectorizable_content,
                    properties = EXCLUDED.properties,
                    updated = EXCLUDED.updated
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                conn.setAutoCommit(false);
                
                for (Node node : nodes) {
                    ps.setObject(1, node.getId());
                    ps.setString(2, node.getLabel());
                    ps.setString(3, node.getVectorizableContent());
                    ps.setString(4, serializeProperties(node.getProperties()));
                    ps.setTimestamp(5, Timestamp.from(node.getCreated() != null ? node.getCreated() : Instant.now()));
                    ps.setTimestamp(6, Timestamp.from(Instant.now()));
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
                
                log.info("Batch added {} nodes", nodes.size());
            }
            return nodes;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            String sql = """
                INSERT INTO cef_edges (id, source_node_id, target_node_id, relation_type, weight, properties, created)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (id) DO UPDATE SET
                    source_node_id = EXCLUDED.source_node_id,
                    target_node_id = EXCLUDED.target_node_id,
                    relation_type = EXCLUDED.relation_type,
                    weight = EXCLUDED.weight,
                    properties = EXCLUDED.properties
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                conn.setAutoCommit(false);
                
                for (Edge edge : edges) {
                    ps.setObject(1, edge.getId());
                    ps.setObject(2, edge.getSourceNodeId());
                    ps.setObject(3, edge.getTargetNodeId());
                    ps.setString(4, edge.getRelationType());
                    ps.setDouble(5, edge.getWeight() != null ? edge.getWeight() : 1.0);
                    ps.setString(6, serializeProperties(edge.getProperties()));
                    ps.setTimestamp(7, Timestamp.from(edge.getCreated() != null ? edge.getCreated() : Instant.now()));
                    ps.addBatch();
                }
                
                ps.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
                
                log.info("Batch added {} edges", edges.size());
            }
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    private Node mapNode(ResultSet rs) throws SQLException {
        Node node = new Node();
        node.setId(rs.getObject("id", UUID.class));
        node.setLabel(rs.getString("label"));
        node.setVectorizableContent(rs.getString("vectorizable_content"));
        node.setProperties(deserializeProperties(rs.getString("properties")));
        node.setCreated(rs.getTimestamp("created").toInstant());
        node.setUpdated(rs.getTimestamp("updated").toInstant());
        return node;
    }

    private Edge mapEdge(ResultSet rs) throws SQLException {
        Edge edge = new Edge();
        edge.setId(rs.getObject("id", UUID.class));
        edge.setSourceNodeId(rs.getObject("source_node_id", UUID.class));
        edge.setTargetNodeId(rs.getObject("target_node_id", UUID.class));
        edge.setRelationType(rs.getString("relation_type"));
        edge.setWeight(rs.getDouble("weight"));
        edge.setProperties(deserializeProperties(rs.getString("properties")));
        edge.setCreated(rs.getTimestamp("created").toInstant());
        return edge;
    }

    private String serializeProperties(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize properties: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> deserializeProperties(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize properties: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public Flux<Edge> getEdgesForNode(UUID nodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Edge> edges = new ArrayList<>();
            
            String sql = """
                SELECT id, source_node_id, target_node_id, relation_type, weight, properties, created
                FROM cef_edges 
                WHERE source_node_id = ? OR target_node_id = ?
                """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, nodeId);
                ps.setObject(2, nodeId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        edges.add(mapEdge(rs));
                    }
                }
            }
            
            log.debug("Found {} edges for node {}", edges.size(), nodeId);
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }
}
