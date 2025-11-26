package org.ddse.ml.cef.storage;

import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;

import java.util.List;

/**
 * Represents a subgraph extracted from the main graph.
 * Used for graph-enhanced context generation.
 *
 * @author mrmanna
 */
public class GraphSubgraph {

    private final List<Node> nodes;
    private final List<Edge> edges;

    public GraphSubgraph(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    @Override
    public String toString() {
        return "GraphSubgraph{" +
                "nodeCount=" + getNodeCount() +
                ", edgeCount=" + getEdgeCount() +
                '}';
    }
}
