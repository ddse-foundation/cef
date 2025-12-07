package org.ddse.ml.cef.graph;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.domain.RelationType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Pluggable interface for graph storage backends.
 * 
 * Implementations:
 * - DuckDbGraphStore: Pure SQL on DuckDB (default)
 * - InMemoryGraphStore: In-memory JGraphT (<100K nodes)
 * - Neo4jGraphStore: Native Neo4j graph database
 * - PgSqlGraphStore: Pure SQL on PostgreSQL
 * - PgAgeGraphStore: Cypher via Apache AGE on PostgreSQL
 * 
 * Framework selects implementation based on application.yml:
 * cef.graph.store: duckdb | in-memory | neo4j | pg-sql | pg-age
 *
 * @author mrmanna
 */
public interface GraphStore {

    /**
     * Initialize the graph store with relation type registry.
     * Must be called before any graph operations.
     */
    Mono<Void> initialize(List<RelationType> relationTypes);

    /**
     * Add or update a node in the graph.
     */
    Mono<Node> addNode(Node node);

    /**
     * Add or update an edge in the graph.
     * Validates against registered relation types.
     */
    Mono<Edge> addEdge(Edge edge);

    /**
     * Retrieve a node by ID.
     */
    Mono<Node> getNode(UUID nodeId);

    /**
     * Retrieve an edge by ID.
     */
    Mono<Edge> getEdge(UUID edgeId);

    /**
     * Find all nodes with a specific label.
     */
    Flux<Node> findNodesByLabel(String label);

    /**
     * Find all edges of a specific relation type.
     */
    Flux<Edge> findEdgesByRelationType(String relationType);

    /**
     * Get all neighbors of a node (1-hop traversal).
     */
    Flux<Node> getNeighbors(UUID nodeId);

    /**
     * Get neighbors connected by specific relation type.
     */
    Flux<Node> getNeighborsByRelationType(UUID nodeId, String relationType);

    /**
     * Find shortest path between two nodes.
     * Returns list of node IDs in path order.
     */
    Mono<List<UUID>> findShortestPath(UUID sourceId, UUID targetId);

    /**
     * Find K-hop neighbors from a starting node.
     * 
     * @param depth Maximum hops (1-3 typical for LLM context window)
     */
    Flux<Node> findKHopNeighbors(UUID nodeId, int depth);

    /**
     * Find subgraph around nodes (for graph-enhanced context).
     * Returns all nodes and edges within k-hops of seed nodes.
     */
    Mono<GraphSubgraph> extractSubgraph(List<UUID> seedNodeIds, int depth);

    /**
     * Delete a node and all its connected edges.
     */
    Mono<Void> deleteNode(UUID nodeId);

    /**
     * Delete an edge.
     */
    Mono<Void> deleteEdge(UUID edgeId);

    /**
     * Clear all graph data (use with caution).
     */
    Mono<Void> clear();

    /**
     * Get graph statistics (node count, edge count, etc.).
     */
    Mono<GraphStats> getStatistics();

    /**
     * Batch load nodes (optimized bulk insert).
     */
    Flux<Node> batchAddNodes(List<Node> nodes);

    /**
     * Batch load edges (optimized bulk insert).
     */
    Flux<Edge> batchAddEdges(List<Edge> edges);

    /**
     * Get all edges connected to a node (incoming and outgoing).
     * Required for pattern-based traversal operations.
     * 
     * @param nodeId The node to get edges for
     * @return Flux of all edges where this node is source or target
     */
    Flux<Edge> getEdgesForNode(UUID nodeId);
}
