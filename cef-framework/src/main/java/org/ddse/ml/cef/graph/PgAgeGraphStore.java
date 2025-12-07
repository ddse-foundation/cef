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
 * PostgreSQL-based GraphStore implementation using Apache AGE extension.
 * 
 * <p>Apache AGE (A Graph Extension) adds native graph database capabilities to PostgreSQL,
 * supporting openCypher query language for graph traversals.</p>
 * 
 * <h3>Prerequisites:</h3>
 * <ul>
 *   <li>PostgreSQL 11+ with Apache AGE extension installed</li>
 *   <li>AGE extension loaded: <code>CREATE EXTENSION IF NOT EXISTS age;</code></li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
public class PgAgeGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(PgAgeGraphStore.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final String graphName;
    private final Set<String> registeredRelationTypes = new HashSet<>();

    public PgAgeGraphStore(DataSource dataSource, String graphName) {
        this.dataSource = dataSource;
        this.graphName = graphName;
        this.objectMapper = new ObjectMapper();
        log.info("PgAgeGraphStore created - Apache AGE Cypher implementation, graph: {}", graphName);
    }

    public PgAgeGraphStore(DataSource dataSource) {
        this(dataSource, "cef_graph");
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        return Mono.fromCallable(() -> {
            log.info("Initializing PgAgeGraphStore with {} relation types", relationTypes.size());
            
            try (Connection conn = dataSource.getConnection()) {
                setupAgeExtension(conn);
                createGraphIfNotExists(conn);
                
                for (RelationType rt : relationTypes) {
                    registeredRelationTypes.add(rt.getName());
                }
                
                log.info("PgAgeGraphStore initialization complete - {} relation types registered", 
                    registeredRelationTypes.size());
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    private void setupAgeExtension(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS age");
            stmt.execute("LOAD 'age'");
            stmt.execute("SET search_path = ag_catalog, \"$user\", public");
            log.debug("AGE extension loaded and search path set");
        }
    }

    private void createGraphIfNotExists(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT count(*) FROM ag_catalog.ag_graph WHERE name = '" + graphName + "'");
            rs.next();
            if (rs.getInt(1) == 0) {
                stmt.execute("SELECT create_graph('" + graphName + "')");
                log.info("Created AGE graph: {}", graphName);
            } else {
                log.debug("AGE graph already exists: {}", graphName);
            }
        }
    }

    private void setAgeSearchPath(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LOAD 'age'");
            stmt.execute("SET search_path = ag_catalog, \"$user\", public");
        }
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return Mono.fromCallable(() -> {
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MERGE (n:CefNode {id: '%s'})
                    SET n.label = '%s',
                        n.vectorizableContent = '%s',
                        n.properties = '%s',
                        n.created = '%s'
                    RETURN n
                $$) AS (n agtype)
                """, 
                graphName,
                node.getId().toString(),
                escapeString(node.getLabel()),
                escapeString(node.getVectorizableContent() != null ? node.getVectorizableContent() : ""),
                escapeString(serializeProperties(node.getProperties())),
                node.getCreated() != null ? node.getCreated().toString() : Instant.now().toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(cypher);
                }
            }
            return node;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return Mono.fromCallable(() -> {
            if (!registeredRelationTypes.isEmpty() && 
                !registeredRelationTypes.contains(edge.getRelationType())) {
                throw new IllegalArgumentException("Unknown relation type: " + edge.getRelationType());
            }
            
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (s:CefNode {id: '%s'}), (t:CefNode {id: '%s'})
                    MERGE (s)-[r:%s {id: '%s'}]->(t)
                    SET r.weight = %f,
                        r.properties = '%s',
                        r.created = '%s'
                    RETURN r
                $$) AS (r agtype)
                """,
                graphName,
                edge.getSourceNodeId().toString(),
                edge.getTargetNodeId().toString(),
                edge.getRelationType(),
                edge.getId().toString(),
                edge.getWeight() != null ? edge.getWeight() : 1.0,
                escapeString(serializeProperties(edge.getProperties())),
                edge.getCreated() != null ? edge.getCreated().toString() : Instant.now().toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(cypher);
                }
            }
            return edge;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode {id: '%s'})
                    RETURN n.id, n.label, n.vectorizableContent, n.properties, n.created
                $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                """,
                graphName,
                nodeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    if (rs.next()) {
                        return mapNodeFromAgtype(rs);
                    }
                    return null;
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (s)-[r {id: '%s'}]->(t)
                    RETURN r.id, s.id, t.id, type(r), r.weight, r.properties, r.created
                $$) AS (id agtype, source_id agtype, target_id agtype, rel_type agtype, weight agtype, properties agtype, created agtype)
                """,
                graphName,
                edgeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    if (rs.next()) {
                        return mapEdgeFromAgtype(rs);
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
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode)
                    WHERE n.label = '%s'
                    RETURN n.id, n.label, n.vectorizableContent, n.properties, n.created
                $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                """,
                graphName,
                escapeString(label)
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    while (rs.next()) {
                        nodes.add(mapNodeFromAgtype(rs));
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
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (s)-[r:%s]->(t)
                    RETURN r.id, s.id, t.id, type(r), r.weight, r.properties, r.created
                $$) AS (id agtype, source_id agtype, target_id agtype, rel_type agtype, weight agtype, properties agtype, created agtype)
                """,
                graphName,
                relationType
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    while (rs.next()) {
                        edges.add(mapEdgeFromAgtype(rs));
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
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode {id: '%s'})-[r]-(neighbor:CefNode)
                    RETURN DISTINCT neighbor.id, neighbor.label, neighbor.vectorizableContent, neighbor.properties, neighbor.created
                $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                """,
                graphName,
                nodeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    while (rs.next()) {
                        neighbors.add(mapNodeFromAgtype(rs));
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
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode {id: '%s'})-[r:%s]-(neighbor:CefNode)
                    RETURN DISTINCT neighbor.id, neighbor.label, neighbor.vectorizableContent, neighbor.properties, neighbor.created
                $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                """,
                graphName,
                nodeId.toString(),
                relationType
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    while (rs.next()) {
                        neighbors.add(mapNodeFromAgtype(rs));
                    }
                }
            }
            return neighbors;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.<List<UUID>>fromCallable(() -> {
            List<UUID> path = new ArrayList<>();
            
            // AGE doesn't support shortestPath() function directly
            // Use variable-length path matching with LIMIT 1 to get shortest path
            // Try progressively longer paths starting from 1
            for (int maxDepth = 1; maxDepth <= 10; maxDepth++) {
                String cypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH p = (s:CefNode {id: '%s'})-[*1..%d]-(t:CefNode {id: '%s'})
                        RETURN [n IN nodes(p) | n.id] AS path
                        LIMIT 1
                    $$) AS (path agtype)
                    """,
                    graphName,
                    sourceId.toString(),
                    maxDepth,
                    targetId.toString()
                );
                
                try (Connection conn = dataSource.getConnection()) {
                    setAgeSearchPath(conn);
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(cypher)) {
                        
                        if (rs.next()) {
                            String pathJson = cleanAgtype(rs.getString("path"));
                            List<String> ids = objectMapper.readValue(pathJson, new TypeReference<List<String>>() {});
                            for (String id : ids) {
                                path.add(UUID.fromString(id.replace("\"", "")));
                            }
                            return path; // Found a path, return it
                        }
                    }
                } catch (SQLException e) {
                    // AGE throws error when nodes don't exist, return empty path
                    log.debug("Path not found: {}", e.getMessage());
                    return path;
                }
            }
            return path; // No path found
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Node> neighbors = new ArrayList<>();
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode {id: '%s'})-[*1..%d]-(neighbor:CefNode)
                    WHERE neighbor.id <> '%s'
                    RETURN DISTINCT neighbor.id, neighbor.label, neighbor.vectorizableContent, neighbor.properties, neighbor.created
                $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                """,
                graphName,
                nodeId.toString(),
                depth,
                nodeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    
                    while (rs.next()) {
                        neighbors.add(mapNodeFromAgtype(rs));
                    }
                }
            }
            return neighbors;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        return Mono.fromCallable(() -> {
            List<Node> nodes = new ArrayList<>();
            List<Edge> edges = new ArrayList<>();
            Set<String> visitedNodeIds = new HashSet<>();
            Set<String> visitedEdgeIds = new HashSet<>();
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                
                for (UUID seedId : seedNodeIds) {
                    // Get nodes within k-hops
                    String nodesCypher = String.format("""
                        SELECT * FROM cypher('%s', $$
                            MATCH path = (seed:CefNode {id: '%s'})-[*0..%d]-(n:CefNode)
                            RETURN DISTINCT n.id, n.label, n.vectorizableContent, n.properties, n.created
                        $$) AS (id agtype, label agtype, vectorizableContent agtype, properties agtype, created agtype)
                        """,
                        graphName,
                        seedId.toString(),
                        depth
                    );
                    
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(nodesCypher)) {
                        
                        while (rs.next()) {
                            Node node = mapNodeFromAgtype(rs);
                            if (!visitedNodeIds.contains(node.getId().toString())) {
                                visitedNodeIds.add(node.getId().toString());
                                nodes.add(node);
                            }
                        }
                    }
                    
                    // Get edges within k-hops
                    String edgesCypher = String.format("""
                        SELECT * FROM cypher('%s', $$
                            MATCH (seed:CefNode {id: '%s'})-[*0..%d]-(n:CefNode)
                            MATCH (s)-[r]->(t)
                            WHERE s.id = n.id OR t.id = n.id
                            RETURN DISTINCT r.id, s.id, t.id, type(r), r.weight, r.properties, r.created
                        $$) AS (id agtype, source_id agtype, target_id agtype, rel_type agtype, weight agtype, properties agtype, created agtype)
                        """,
                        graphName,
                        seedId.toString(),
                        depth
                    );
                    
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(edgesCypher)) {
                        
                        while (rs.next()) {
                            Edge edge = mapEdgeFromAgtype(rs);
                            if (edge != null && !visitedEdgeIds.contains(edge.getId().toString())) {
                                visitedEdgeIds.add(edge.getId().toString());
                                edges.add(edge);
                            }
                        }
                    } catch (SQLException e) {
                        // Edge query may fail if no edges exist, log and continue
                        log.debug("No edges found in subgraph: {}", e.getMessage());
                    }
                }
            }
            
            return new GraphSubgraph(nodes, edges);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH (n:CefNode {id: '%s'})
                    DETACH DELETE n
                $$) AS (result agtype)
                """,
                graphName,
                nodeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(cypher);
                }
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            String cypher = String.format("""
                SELECT * FROM cypher('%s', $$
                    MATCH ()-[r {id: '%s'}]->()
                    DELETE r
                $$) AS (result agtype)
                """,
                graphName,
                edgeId.toString()
            );
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(cypher);
                }
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                try (Statement stmt = conn.createStatement()) {
                    String cypher = String.format("""
                        SELECT * FROM cypher('%s', $$
                            MATCH (n)
                            DETACH DELETE n
                        $$) AS (result agtype)
                        """, graphName);
                    stmt.execute(cypher);
                    log.info("Cleared all graph data from AGE graph: {}", graphName);
                }
            }
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.fromCallable(() -> {
            long nodeCount = 0;
            long edgeCount = 0;
            Map<String, Long> nodesByLabel = new HashMap<>();
            Map<String, Long> edgesByType = new HashMap<>();
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                
                String countNodesCypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH (n:CefNode)
                        RETURN count(n) AS cnt
                    $$) AS (cnt agtype)
                    """, graphName);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(countNodesCypher)) {
                    if (rs.next()) {
                        nodeCount = Long.parseLong(cleanAgtype(rs.getString("cnt")));
                    }
                }
                
                String countEdgesCypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH ()-[r]->()
                        RETURN count(r) AS cnt
                    $$) AS (cnt agtype)
                    """, graphName);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(countEdgesCypher)) {
                    if (rs.next()) {
                        edgeCount = Long.parseLong(cleanAgtype(rs.getString("cnt")));
                    }
                }
                
                String nodesByLabelCypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH (n:CefNode)
                        RETURN n.label AS label, count(n) AS cnt
                    $$) AS (label agtype, cnt agtype)
                    """, graphName);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(nodesByLabelCypher)) {
                    while (rs.next()) {
                        String label = cleanAgtype(rs.getString("label")).replace("\"", "");
                        long count = Long.parseLong(cleanAgtype(rs.getString("cnt")));
                        nodesByLabel.put(label, count);
                    }
                }
                
                String edgesByTypeCypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH ()-[r]->()
                        RETURN type(r) AS rel_type, count(r) AS cnt
                    $$) AS (rel_type agtype, cnt agtype)
                    """, graphName);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(edgesByTypeCypher)) {
                    while (rs.next()) {
                        String relType = cleanAgtype(rs.getString("rel_type")).replace("\"", "");
                        long count = Long.parseLong(cleanAgtype(rs.getString("cnt")));
                        edgesByType.put(relType, count);
                    }
                }
            }
            
            double avgDegree = nodeCount > 0 ? (2.0 * edgeCount) / nodeCount : 0.0;
            
            return new GraphStats(nodeCount, edgeCount, nodesByLabel, edgesByType, avgDegree);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                conn.setAutoCommit(false);
                
                try (Statement stmt = conn.createStatement()) {
                    for (Node node : nodes) {
                        String cypher = String.format("""
                            SELECT * FROM cypher('%s', $$
                                MERGE (n:CefNode {id: '%s'})
                                SET n.label = '%s',
                                    n.vectorizableContent = '%s',
                                    n.properties = '%s',
                                    n.created = '%s'
                                RETURN n
                            $$) AS (n agtype)
                            """,
                            graphName,
                            node.getId().toString(),
                            escapeString(node.getLabel()),
                            escapeString(node.getVectorizableContent() != null ? node.getVectorizableContent() : ""),
                            escapeString(serializeProperties(node.getProperties())),
                            node.getCreated() != null ? node.getCreated().toString() : Instant.now().toString()
                        );
                        stmt.execute(cypher);
                    }
                }
                
                conn.commit();
                conn.setAutoCommit(true);
                log.info("Batch added {} nodes to AGE graph", nodes.size());
            }
            return nodes;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                conn.setAutoCommit(false);
                
                try (Statement stmt = conn.createStatement()) {
                    for (Edge edge : edges) {
                        String cypher = String.format("""
                            SELECT * FROM cypher('%s', $$
                                MATCH (s:CefNode {id: '%s'}), (t:CefNode {id: '%s'})
                                MERGE (s)-[r:%s {id: '%s'}]->(t)
                                SET r.weight = %f,
                                    r.properties = '%s',
                                    r.created = '%s'
                                RETURN r
                            $$) AS (r agtype)
                            """,
                            graphName,
                            edge.getSourceNodeId().toString(),
                            edge.getTargetNodeId().toString(),
                            edge.getRelationType(),
                            edge.getId().toString(),
                            edge.getWeight() != null ? edge.getWeight() : 1.0,
                            escapeString(serializeProperties(edge.getProperties())),
                            edge.getCreated() != null ? edge.getCreated().toString() : Instant.now().toString()
                        );
                        stmt.execute(cypher);
                    }
                }
                
                conn.commit();
                conn.setAutoCommit(true);
                log.info("Batch added {} edges to AGE graph", edges.size());
            }
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    private Node mapNodeFromAgtype(ResultSet rs) throws SQLException {
        Node node = new Node();
        node.setId(UUID.fromString(cleanAgtype(rs.getString("id")).replace("\"", "")));
        node.setLabel(cleanAgtype(rs.getString("label")).replace("\"", ""));
        node.setVectorizableContent(cleanAgtype(rs.getString("vectorizableContent")).replace("\"", ""));
        node.setProperties(deserializeProperties(cleanAgtype(rs.getString("properties"))));
        
        String createdStr = cleanAgtype(rs.getString("created")).replace("\"", "");
        if (createdStr != null && !createdStr.isEmpty() && !"null".equals(createdStr)) {
            try {
                node.setCreated(Instant.parse(createdStr));
            } catch (Exception e) {
                node.setCreated(Instant.now());
            }
        } else {
            node.setCreated(Instant.now());
        }
        
        return node;
    }

    private Edge mapEdgeFromAgtype(ResultSet rs) throws SQLException {
        Edge edge = new Edge();
        
        String idStr = cleanAgtype(rs.getString("id")).replace("\"", "");
        if (idStr != null && !idStr.isEmpty() && !"null".equals(idStr)) {
            edge.setId(UUID.fromString(idStr));
        }
        
        edge.setSourceNodeId(UUID.fromString(cleanAgtype(rs.getString("source_id")).replace("\"", "")));
        edge.setTargetNodeId(UUID.fromString(cleanAgtype(rs.getString("target_id")).replace("\"", "")));
        edge.setRelationType(cleanAgtype(rs.getString("rel_type")).replace("\"", ""));
        
        String weightStr = cleanAgtype(rs.getString("weight"));
        if (weightStr != null && !weightStr.isEmpty() && !"null".equals(weightStr)) {
            edge.setWeight(Double.parseDouble(weightStr));
        } else {
            edge.setWeight(1.0);
        }
        
        edge.setProperties(deserializeProperties(cleanAgtype(rs.getString("properties"))));
        
        String createdStr = cleanAgtype(rs.getString("created")).replace("\"", "");
        if (createdStr != null && !createdStr.isEmpty() && !"null".equals(createdStr)) {
            try {
                edge.setCreated(Instant.parse(createdStr));
            } catch (Exception e) {
                edge.setCreated(Instant.now());
            }
        } else {
            edge.setCreated(Instant.now());
        }
        
        return edge;
    }

    private String cleanAgtype(String agtype) {
        if (agtype == null) return "";
        int idx = agtype.lastIndexOf("::");
        if (idx > 0) {
            return agtype.substring(0, idx);
        }
        return agtype;
    }

    private String escapeString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
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
        if (json == null || json.isEmpty() || "{}".equals(json) || "\"{}\"".equals(json)) {
            return new HashMap<>();
        }
        try {
            String cleanJson = json;
            if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                cleanJson = cleanJson.substring(1, cleanJson.length() - 1);
                cleanJson = cleanJson.replace("\\\"", "\"");
            }
            return objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize properties: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @Override
    public Flux<Edge> getEdgesForNode(UUID nodeId) {
        return Flux.defer(() -> Mono.fromCallable(() -> {
            List<Edge> edges = new ArrayList<>();
            
            try (Connection conn = dataSource.getConnection()) {
                setAgeSearchPath(conn);
                
                // Query for edges where this node is source or target
                String cypher = String.format("""
                    SELECT * FROM cypher('%s', $$
                        MATCH (s)-[r]->(t)
                        WHERE s.id = '%s' OR t.id = '%s'
                        RETURN r.id, s.id, t.id, type(r), r.weight, r.properties, r.created
                    $$) AS (id agtype, source_id agtype, target_id agtype, rel_type agtype, weight agtype, properties agtype, created agtype)
                    """, graphName, nodeId, nodeId);
                
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(cypher)) {
                    while (rs.next()) {
                        edges.add(mapEdgeFromAgtype(rs));
                    }
                }
            }
            
            log.debug("Found {} edges for node {}", edges.size(), nodeId);
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }
}
