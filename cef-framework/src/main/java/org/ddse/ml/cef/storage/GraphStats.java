package org.ddse.ml.cef.storage;

import java.util.Map;

/**
 * Graph statistics for monitoring and diagnostics.
 *
 * @author mrmanna
 */
public class GraphStats {

    private final long nodeCount;
    private final long edgeCount;
    private final Map<String, Long> nodeCountByLabel;
    private final Map<String, Long> edgeCountByType;
    private final double averageDegree;

    public GraphStats(long nodeCount, long edgeCount,
            Map<String, Long> nodeCountByLabel,
            Map<String, Long> edgeCountByType,
            double averageDegree) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.nodeCountByLabel = nodeCountByLabel;
        this.edgeCountByType = edgeCountByType;
        this.averageDegree = averageDegree;
    }

    public long getNodeCount() {
        return nodeCount;
    }

    public long getEdgeCount() {
        return edgeCount;
    }

    public Map<String, Long> getNodeCountByLabel() {
        return nodeCountByLabel;
    }

    public Map<String, Long> getEdgeCountByType() {
        return edgeCountByType;
    }

    public double getAverageDegree() {
        return averageDegree;
    }

    @Override
    public String toString() {
        return "GraphStats{" +
                "nodeCount=" + nodeCount +
                ", edgeCount=" + edgeCount +
                ", nodeCountByLabel=" + nodeCountByLabel +
                ", edgeCountByType=" + edgeCountByType +
                ", averageDegree=" + String.format("%.2f", averageDegree) +
                '}';
    }
}
