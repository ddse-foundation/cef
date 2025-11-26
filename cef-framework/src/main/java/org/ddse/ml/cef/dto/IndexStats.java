package org.ddse.ml.cef.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * Statistics DTO for indexing operations.
 * Provides metrics about the indexed knowledge graph.
 */
public record IndexStats(
        long totalNodes,
        long totalEdges,
        long totalChunks,
        Map<String, Long> nodeLabelCounts,
        Map<String, Long> edgeTypeCounts,
        long avgEmbeddingTimeMs,
        Map<String, Object> additionalMetrics) {
    public IndexStats {
        nodeLabelCounts = nodeLabelCounts != null ? Map.copyOf(nodeLabelCounts) : Map.of();
        edgeTypeCounts = edgeTypeCounts != null ? Map.copyOf(edgeTypeCounts) : Map.of();
        additionalMetrics = additionalMetrics != null ? Map.copyOf(additionalMetrics) : Map.of();
    }

    public long totalGraphElements() {
        return totalNodes + totalEdges;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalNodes;
        private long totalEdges;
        private long totalChunks;
        private Map<String, Long> nodeLabelCounts = new HashMap<>();
        private Map<String, Long> edgeTypeCounts = new HashMap<>();
        private long avgEmbeddingTimeMs;
        private Map<String, Object> additionalMetrics = new HashMap<>();

        public Builder totalNodes(long totalNodes) {
            this.totalNodes = totalNodes;
            return this;
        }

        public Builder totalEdges(long totalEdges) {
            this.totalEdges = totalEdges;
            return this;
        }

        public Builder totalChunks(long totalChunks) {
            this.totalChunks = totalChunks;
            return this;
        }

        public Builder nodeLabelCounts(Map<String, Long> counts) {
            this.nodeLabelCounts = new HashMap<>(counts);
            return this;
        }

        public Builder edgeTypeCounts(Map<String, Long> counts) {
            this.edgeTypeCounts = new HashMap<>(counts);
            return this;
        }

        public Builder avgEmbeddingTimeMs(long avgEmbeddingTimeMs) {
            this.avgEmbeddingTimeMs = avgEmbeddingTimeMs;
            return this;
        }

        public Builder additionalMetrics(Map<String, Object> metrics) {
            this.additionalMetrics = new HashMap<>(metrics);
            return this;
        }

        public Builder addMetric(String key, Object value) {
            this.additionalMetrics.put(key, value);
            return this;
        }

        public IndexStats build() {
            return new IndexStats(
                    totalNodes, totalEdges, totalChunks,
                    nodeLabelCounts, edgeTypeCounts,
                    avgEmbeddingTimeMs, additionalMetrics);
        }
    }
}
