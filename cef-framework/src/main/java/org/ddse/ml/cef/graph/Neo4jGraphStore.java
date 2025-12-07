package org.ddse.ml.cef.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.MapAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j-based implementation of GraphStore for production-scale graph operations.
 * 
 * <p>Uses Neo4j Community Edition 5.x with native Cypher queries for optimal performance.
 * Designed for millions of nodes/edges with enterprise-grade durability and ACID compliance.
 * 
 * <h2>Activation</h2>
 * <pre>
 * cef:
 *   graph:
 *     type: neo4j
 * spring:
 *   neo4j:
 *     uri: bolt://localhost:7687
 *     authentication:
 *       username: neo4j
 *       password: password
 * </pre>
 * 
 * <h2>Schema Design</h2>
 * <ul>
 *   <li>Nodes: {@code (n:CefNode {id: UUID, label: String, properties: Map, vectorizableContent: String})}</li>
 *   <li>Edges: Dynamic relationship types with CEF prefix: {@code [:CEF_TREATS], [:CEF_CONTAINS], etc.}</li>
 *   <li>Indexes: On id (unique), label, and optionally embedding vector</li>
 * </ul>
 * 
 * <p><b>Note:</b> This class is NOT a @Component. It is created by GraphStoreAutoConfiguration
 * when cef.graph.store=neo4j is set.</p>
 * 
 * @author mrmanna
 * @since v0.6
 */
public class Neo4jGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphStore.class);
    
    private static final String NODE_LABEL = "CefNode";
    private static final String EDGE_PREFIX = "CEF_";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Driver driver;
    private final String databaseName;
    private final Set<String> registeredRelationTypes = new HashSet<>();
    private volatile boolean initialized = false;

    public Neo4jGraphStore(Driver driver) {
        this(driver, "neo4j");
    }

    public Neo4jGraphStore(Driver driver, String databaseName) {
        this.driver = driver;
        this.databaseName = (databaseName != null && !databaseName.isBlank()) ? databaseName : "neo4j";
        log.info("Neo4jGraphStore created for database '{}' - production-scale graph store initialized", this.databaseName);
    }

    @Override
    public Mono<Void> initialize(List<RelationType> relationTypes) {
        return Mono.fromRunnable(() -> {
            log.info("Initializing Neo4jGraphStore with {} relation types", relationTypes.size());
            
            try (Session session = driver.session(sessionConfig())) {
                // Create unique constraint on node ID
                session.executeWrite(tx -> {
                    tx.run("""
                        CREATE CONSTRAINT cef_node_id IF NOT EXISTS
                        FOR (n:CefNode) REQUIRE n.id IS UNIQUE
                        """);
                    return null;
                });
                
                // Create index on node label for fast lookups
                session.executeWrite(tx -> {
                    tx.run("""
                        CREATE INDEX cef_node_label IF NOT EXISTS
                        FOR (n:CefNode) ON (n.label)
                        """);
                    return null;
                });
                
                // Register relation types
                for (RelationType relationType : relationTypes) {
                    registeredRelationTypes.add(relationType.getName());
                    log.debug("Registered relation type: {}", relationType.getName());
                }
                
                initialized = true;
                log.info("Neo4jGraphStore initialization complete - {} relation types registered", 
                        registeredRelationTypes.size());
            } catch (Exception e) {
                log.error("Failed to initialize Neo4jGraphStore", e);
                throw new RuntimeException("Neo4j initialization failed", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Node> addNode(Node node) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                return session.executeWrite(tx -> {
                    String cypher = """
                        MERGE (n:CefNode {id: $id})
                        SET n.label = $label,
                            n.properties = $properties,
                            n.vectorizableContent = $vectorizableContent,
                            n.created = $created,
                            n.updated = $updated
                        RETURN n
                        """;
                    
                    Result result = tx.run(cypher, Map.of(
                            "id", node.getId().toString(),
                            "label", node.getLabel() != null ? node.getLabel() : "",
                            "properties", convertPropertiesToJson(node.getProperties()),
                            "vectorizableContent", node.getVectorizableContent() != null ? node.getVectorizableContent() : "",
                            "created", node.getCreated().toString(),
                            "updated", Instant.now().toString()
                    ));
                    
                    if (result.hasNext()) {
                        Record record = result.next();
                        log.debug("Added/updated node: {} with label: {}", node.getId(), node.getLabel());
                        return node;
                    }
                    return node;
                });
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> addEdge(Edge edge) {
        return Mono.fromCallable(() -> {
            // Validate relation type
            if (!registeredRelationTypes.contains(edge.getRelationType())) {
                log.warn("Unregistered relation type: {}. Consider registering it during initialization.", 
                        edge.getRelationType());
            }
            
            try (Session session = driver.session(sessionConfig())) {
                return session.executeWrite(tx -> {
                    String relationType = EDGE_PREFIX + edge.getRelationType().toUpperCase();
                    
                    // Dynamic relationship type via APOC or parameterized query
                    String cypher = String.format("""
                        MATCH (source:CefNode {id: $sourceId})
                        MATCH (target:CefNode {id: $targetId})
                        MERGE (source)-[r:%s {id: $edgeId}]->(target)
                        SET r.properties = $properties,
                            r.weight = $weight,
                            r.created = $created
                        RETURN r
                        """, relationType);
                    
                    Result result = tx.run(cypher, Map.of(
                            "sourceId", edge.getSourceNodeId().toString(),
                            "targetId", edge.getTargetNodeId().toString(),
                            "edgeId", edge.getId().toString(),
                            "properties", convertPropertiesToJson(edge.getProperties()),
                            "weight", edge.getWeight() != null ? edge.getWeight() : 1.0,
                            "created", edge.getCreated().toString()
                    ));
                    
                    if (result.hasNext()) {
                        log.debug("Added/updated edge: {} ({} -> {})", 
                                edge.getId(), edge.getSourceNodeId(), edge.getTargetNodeId());
                        return edge;
                    }
                    return edge;
                });
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Node> getNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run("""
                        MATCH (n:CefNode {id: $id})
                        RETURN n
                        """, Map.of("id", nodeId.toString()));
                
                if (result.hasNext()) {
                    return mapRecordToNode(result.next().get("n").asMap());
                }
                return null;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Edge> getEdge(UUID edgeId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run("""
                        MATCH (source:CefNode)-[r {id: $id}]->(target:CefNode)
                        RETURN r, source.id AS sourceId, target.id AS targetId, type(r) AS relType
                        """, Map.of("id", edgeId.toString()));
                
                if (result.hasNext()) {
                    Record record = result.next();
                    return mapRecordToEdge(record);
                }
                return null;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findNodesByLabel(String label) {
        return Flux.defer(() -> {
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run("""
                        MATCH (n:CefNode {label: $label})
                        RETURN n
                        """, Map.of("label", label));
                
                List<Node> nodes = new ArrayList<>();
                while (result.hasNext()) {
                    nodes.add(mapRecordToNode(result.next().get("n").asMap()));
                }
                return Flux.fromIterable(nodes);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> findEdgesByRelationType(String relationType) {
        return Flux.defer(() -> {
            String neo4jRelType = EDGE_PREFIX + relationType.toUpperCase();
            try (Session session = driver.session(sessionConfig())) {
                String cypher = String.format("""
                        MATCH (source:CefNode)-[r:%s]->(target:CefNode)
                        RETURN r, source.id AS sourceId, target.id AS targetId, type(r) AS relType
                        """, neo4jRelType);
                
                Result result = session.run(cypher);
                
                List<Edge> edges = new ArrayList<>();
                while (result.hasNext()) {
                    edges.add(mapRecordToEdge(result.next()));
                }
                return Flux.fromIterable(edges);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> getNeighbors(UUID nodeId) {
        return Flux.defer(() -> {
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run("""
                        MATCH (n:CefNode {id: $id})-[r]-(neighbor:CefNode)
                        RETURN DISTINCT neighbor
                        """, Map.of("id", nodeId.toString()));
                
                List<Node> nodes = new ArrayList<>();
                while (result.hasNext()) {
                    nodes.add(mapRecordToNode(result.next().get("neighbor").asMap()));
                }
                return Flux.fromIterable(nodes);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType) {
        return Flux.defer(() -> {
            String neo4jRelType = EDGE_PREFIX + relationType.toUpperCase();
            try (Session session = driver.session(sessionConfig())) {
                String cypher = String.format("""
                        MATCH (n:CefNode {id: $id})-[r:%s]-(neighbor:CefNode)
                        RETURN DISTINCT neighbor
                        """, neo4jRelType);
                
                Result result = session.run(cypher, Map.of("id", nodeId.toString()));
                
                List<Node> nodes = new ArrayList<>();
                while (result.hasNext()) {
                    nodes.add(mapRecordToNode(result.next().get("neighbor").asMap()));
                }
                return Flux.fromIterable(nodes);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run("""
                        MATCH path = shortestPath((source:CefNode {id: $sourceId})-[*]-(target:CefNode {id: $targetId}))
                        RETURN [node IN nodes(path) | node.id] AS nodeIds
                        """, Map.of("sourceId", sourceId.toString(), "targetId", targetId.toString()));
                
                if (result.hasNext()) {
                    List<Object> nodeIds = result.next().get("nodeIds").asList();
                    List<UUID> uuidList = new ArrayList<>();
                    for (Object id : nodeIds) {
                        uuidList.add(UUID.fromString(id.toString()));
                    }
                    return uuidList;
                }
                return new ArrayList<UUID>();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> findKHopNeighbors(UUID nodeId, int depth) {
        return Flux.defer(() -> {
            try (Session session = driver.session(sessionConfig())) {
                String cypher = String.format("""
                        MATCH (start:CefNode {id: $id})-[*1..%d]-(neighbor:CefNode)
                        WHERE neighbor.id <> $id
                        RETURN DISTINCT neighbor
                        """, depth);
                
                Result result = session.run(cypher, Map.of("id", nodeId.toString()));
                
                List<Node> nodes = new ArrayList<>();
                while (result.hasNext()) {
                    nodes.add(mapRecordToNode(result.next().get("neighbor").asMap()));
                }
                return Flux.fromIterable(nodes);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth) {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                List<String> seedIds = seedNodeIds.stream()
                        .map(UUID::toString)
                        .collect(Collectors.toList());
                
                String cypher = String.format("""
                        MATCH path = (seed:CefNode)-[*0..%d]-(connected:CefNode)
                        WHERE seed.id IN $seedIds
                        WITH collect(DISTINCT connected) AS allNodes, collect(DISTINCT relationships(path)) AS allRels
                        UNWIND allNodes AS node
                        WITH allNodes, allRels, collect(node) AS nodes
                        UNWIND allRels AS relList
                        UNWIND relList AS rel
                        WITH allNodes, collect(DISTINCT rel) AS rels
                        RETURN allNodes, rels
                        """, depth);
                
                Result nodeResult = session.run(cypher, Map.of("seedIds", seedIds));
                
                Set<Node> nodes = new HashSet<>();
                Set<Edge> edges = new HashSet<>();
                
                if (nodeResult.hasNext()) {
                    Record record = nodeResult.next();
                    
                    // Extract nodes
                    List<Object> nodeRecords = record.get("allNodes").asList();
                    for (Object nodeRecord : nodeRecords) {
                        if (nodeRecord instanceof MapAccessor) {
                            nodes.add(mapRecordToNode(((MapAccessor) nodeRecord).asMap()));
                        }
                    }
                }

                // Extract edges within the same subgraph up to depth
                String edgeCypher = String.format("""
                        MATCH path = (seed:CefNode)-[rel*0..%d]-(connected:CefNode)
                        WHERE seed.id IN $seedIds
                        UNWIND relationships(path) AS r
                        RETURN DISTINCT r AS r,
                               startNode(r).id AS sourceId,
                               endNode(r).id AS targetId,
                               type(r) AS relType
                        """, depth);

                Result edgeResult = session.run(edgeCypher, Map.of("seedIds", seedIds));
                while (edgeResult.hasNext()) {
                    edges.add(mapRecordToEdge(edgeResult.next()));
                }
                
                return new GraphSubgraph(List.copyOf(nodes), List.copyOf(edges));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> deleteNode(UUID nodeId) {
        return Mono.fromRunnable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                session.executeWrite(tx -> {
                    tx.run("""
                            MATCH (n:CefNode {id: $id})
                            DETACH DELETE n
                            """, Map.of("id", nodeId.toString()));
                    return null;
                });
                log.debug("Deleted node: {}", nodeId);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> deleteEdge(UUID edgeId) {
        return Mono.fromRunnable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                session.executeWrite(tx -> {
                    tx.run("""
                            MATCH ()-[r {id: $id}]-()
                            DELETE r
                            """, Map.of("id", edgeId.toString()));
                    return null;
                });
                log.debug("Deleted edge: {}", edgeId);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                session.executeWrite(tx -> {
                    tx.run("MATCH (n:CefNode) DETACH DELETE n");
                    return null;
                });
                log.info("Cleared all CefNode data from Neo4j");
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    public Mono<GraphStats> getStatistics() {
        return Mono.fromCallable(() -> {
            try (Session session = driver.session(sessionConfig())) {
                // Node count
                long nodeCount = session.run("MATCH (n:CefNode) RETURN count(n) AS count")
                        .single().get("count").asLong();
                
                // Edge count
                long edgeCount = session.run("MATCH (:CefNode)-[r]->(:CefNode) RETURN count(r) AS count")
                        .single().get("count").asLong();
                
                // Nodes by label
                Map<String, Long> nodesByLabel = new HashMap<>();
                Result labelResult = session.run("""
                        MATCH (n:CefNode)
                        RETURN n.label AS label, count(n) AS count
                        """);
                while (labelResult.hasNext()) {
                    Record record = labelResult.next();
                    String label = record.get("label").asString();
                    long count = record.get("count").asLong();
                    if (label != null && !label.isEmpty()) {
                        nodesByLabel.put(label, count);
                    }
                }
                
                // Edges by type
                Map<String, Long> edgesByType = new HashMap<>();
                Result typeResult = session.run("""
                        MATCH (:CefNode)-[r]->(:CefNode)
                        RETURN type(r) AS relType, count(r) AS count
                        """);
                while (typeResult.hasNext()) {
                    Record record = typeResult.next();
                    String relType = record.get("relType").asString();
                    long count = record.get("count").asLong();
                    // Remove CEF_ prefix for display
                    if (relType.startsWith(EDGE_PREFIX)) {
                        relType = relType.substring(EDGE_PREFIX.length());
                    }
                    edgesByType.put(relType, count);
                }
                
                // Average degree
                double avgDegree = nodeCount > 0 ? (double) (edgeCount * 2) / nodeCount : 0.0;
                
                return new GraphStats(nodeCount, edgeCount, nodesByLabel, edgesByType, avgDegree);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Node> batchAddNodes(List<Node> nodes) {
        return Flux.defer(() -> {
            try (Session session = driver.session(sessionConfig())) {
                List<Map<String, Object>> nodeParams = nodes.stream()
                        .map(node -> Map.<String, Object>of(
                                "id", node.getId().toString(),
                                "label", node.getLabel() != null ? node.getLabel() : "",
                                "properties", convertPropertiesToJson(node.getProperties()),
                                "vectorizableContent", node.getVectorizableContent() != null ? node.getVectorizableContent() : "",
                                "created", node.getCreated().toString(),
                                "updated", Instant.now().toString()
                        ))
                        .collect(Collectors.toList());
                
                session.executeWrite(tx -> {
                    tx.run("""
                            UNWIND $nodes AS nodeData
                            MERGE (n:CefNode {id: nodeData.id})
                            SET n.label = nodeData.label,
                                n.properties = nodeData.properties,
                                n.vectorizableContent = nodeData.vectorizableContent,
                                n.created = nodeData.created,
                                n.updated = nodeData.updated
                            """, Map.of("nodes", nodeParams));
                    return null;
                });
                
                log.info("Batch added {} nodes", nodes.size());
                return Flux.fromIterable(nodes);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Edge> batchAddEdges(List<Edge> edges) {
        // Group edges by relation type for batch processing
        Map<String, List<Edge>> edgesByType = edges.stream()
                .collect(Collectors.groupingBy(Edge::getRelationType));
        
        return Flux.fromIterable(edgesByType.entrySet())
                .flatMap(entry -> batchAddEdgesOfType(entry.getKey(), entry.getValue()))
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    private Flux<Edge> batchAddEdgesOfType(String relationType, List<Edge> edges) {
        return Flux.defer(() -> {
            String neo4jRelType = EDGE_PREFIX + relationType.toUpperCase();
            
            try (Session session = driver.session(sessionConfig())) {
                List<Map<String, Object>> edgeParams = edges.stream()
                        .map(edge -> Map.<String, Object>of(
                                "edgeId", edge.getId().toString(),
                                "sourceId", edge.getSourceNodeId().toString(),
                                "targetId", edge.getTargetNodeId().toString(),
                                "properties", convertPropertiesToJson(edge.getProperties()),
                                "weight", edge.getWeight() != null ? edge.getWeight() : 1.0,
                                "created", edge.getCreated().toString()
                        ))
                        .collect(Collectors.toList());
                
                String cypher = String.format("""
                        UNWIND $edges AS edgeData
                        MATCH (source:CefNode {id: edgeData.sourceId})
                        MATCH (target:CefNode {id: edgeData.targetId})
                        MERGE (source)-[r:%s {id: edgeData.edgeId}]->(target)
                        SET r.properties = edgeData.properties,
                            r.weight = edgeData.weight,
                            r.created = edgeData.created
                        """, neo4jRelType);
                
                session.executeWrite(tx -> {
                    tx.run(cypher, Map.of("edges", edgeParams));
                    return null;
                });
                
                log.info("Batch added {} edges of type {}", edges.size(), relationType);
                return Flux.fromIterable(edges);
            }
        });
    }

    // ==================== Helper Methods ====================
    
    /**
     * Convert properties map to JSON string for Neo4j storage.
     * Neo4j doesn't support Map as property value, so we serialize to JSON.
     */
    private String convertPropertiesToJson(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize properties to JSON, using empty object", e);
            return "{}";
        }
    }
    
    /**
     * Parse JSON string back to properties map.
     */
    private Map<String, Object> parsePropertiesFromJson(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse properties from JSON: {}", json, e);
            return new HashMap<>();
        }
    }
    
    @SuppressWarnings("unchecked")
    private Node mapRecordToNode(Map<String, Object> record) {
        UUID id = UUID.fromString((String) record.get("id"));
        String label = (String) record.get("label");
        
        // Properties are stored as JSON string
        String propsJson = record.get("properties") instanceof String 
                ? (String) record.get("properties") 
                : "{}";
        Map<String, Object> properties = parsePropertiesFromJson(propsJson);
        
        String vectorizableContent = (String) record.get("vectorizableContent");
        
        Node node = new Node(id, label, properties, vectorizableContent);
        
        // Parse timestamps if present
        if (record.get("created") instanceof String) {
            node.setCreated(Instant.parse((String) record.get("created")));
        }
        if (record.get("updated") instanceof String) {
            node.setUpdated(Instant.parse((String) record.get("updated")));
        }
        
        return node;
    }
    
    @SuppressWarnings("unchecked")
    private Edge mapRecordToEdge(Record record) {
        Map<String, Object> relProps = record.get("r").asMap();
        UUID edgeId = UUID.fromString((String) relProps.get("id"));
        UUID sourceId = UUID.fromString(record.get("sourceId").asString());
        UUID targetId = UUID.fromString(record.get("targetId").asString());
        
        String relType = record.get("relType").asString();
        // Remove CEF_ prefix
        if (relType.startsWith(EDGE_PREFIX)) {
            relType = relType.substring(EDGE_PREFIX.length());
        }
        
        // Properties are stored as JSON string
        String propsJson = relProps.get("properties") instanceof String 
                ? (String) relProps.get("properties") 
                : "{}";
        Map<String, Object> properties = parsePropertiesFromJson(propsJson);
        
        Double weight = relProps.get("weight") instanceof Number 
                ? ((Number) relProps.get("weight")).doubleValue() 
                : 1.0;
        
        return new Edge(edgeId, relType, sourceId, targetId, properties, weight);
    }
    
    private SessionConfig sessionConfig() {
        return SessionConfig.forDatabase(databaseName);
    }
    
    /**
     * Check if Neo4j connection is healthy.
     * Used by health indicators.
     */
    public boolean isHealthy() {
        try (Session session = driver.session(sessionConfig())) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if the store is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public Flux<Edge> getEdgesForNode(UUID nodeId) {
        return Mono.fromCallable(() -> {
            List<Edge> edges = new ArrayList<>();
            
            String cypher = """
                MATCH (n:CefNode {id: $nodeId})-[r]-(m:CefNode)
                RETURN r, n.id as sourceId, m.id as targetId, type(r) as relType
                """;
            
            try (Session session = driver.session(sessionConfig())) {
                Result result = session.run(cypher, Map.of("nodeId", nodeId.toString()));
                while (result.hasNext()) {
                    Record record = result.next();
                    edges.add(mapRecordToEdge(record));
                }
            }
            
            log.debug("Found {} edges for node {}", edges.size(), nodeId);
            return edges;
        }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable);
    }
}
