package org.ddse.ml.cef.benchmark.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Data transfer object for benchmark results.
 * Structured for JSON serialization to feed Python evaluation scripts.
 *
 * @author mrmanna
 * @since v0.6
 */
public class BenchmarkResult {

    private String backend;
    private String dataset;
    private Instant timestamp;
    private DatasetStats datasetStats;
    private List<ScenarioResult> scenarios = new ArrayList<>();
    private Summary summary;

    public BenchmarkResult(String backend, String dataset) {
        this.backend = backend;
        this.dataset = dataset;
        this.timestamp = Instant.now();
    }

    public void addScenario(ScenarioResult scenario) {
        scenarios.add(scenario);
    }

    public void computeSummary() {
        if (scenarios.isEmpty()) {
            summary = new Summary(0, 0, 0);
            return;
        }

        double avgChunkImprovement = scenarios.stream()
                .mapToDouble(s -> {
                    if (s.vectorOnlyChunks == 0) return 0;
                    return ((double)(s.knowledgeModelChunks - s.vectorOnlyChunks) / s.vectorOnlyChunks) * 100;
                })
                .average()
                .orElse(0);

        double avgLatencyOverhead = scenarios.stream()
                .mapToDouble(s -> {
                    if (s.vectorLatencyMs.p50 == 0) return 0;
                    return ((double)(s.kmLatencyMs.p50 - s.vectorLatencyMs.p50) / s.vectorLatencyMs.p50) * 100;
                })
                .average()
                .orElse(0);

        long totalTime = scenarios.stream()
                .mapToLong(s -> s.totalTimeMs)
                .sum();

        summary = new Summary(avgChunkImprovement, avgLatencyOverhead, totalTime);
    }

    // Getters and setters
    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }

    public String getDataset() { return dataset; }
    public void setDataset(String dataset) { this.dataset = dataset; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public DatasetStats getDatasetStats() { return datasetStats; }
    public void setDatasetStats(DatasetStats datasetStats) { this.datasetStats = datasetStats; }

    public List<ScenarioResult> getScenarios() { return scenarios; }
    public void setScenarios(List<ScenarioResult> scenarios) { this.scenarios = scenarios; }

    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }

    /**
     * Dataset statistics.
     */
    public static class DatasetStats {
        private int nodes;
        private int edges;
        private List<String> labels;

        public DatasetStats() {}

        public DatasetStats(int nodes, int edges, List<String> labels) {
            this.nodes = nodes;
            this.edges = edges;
            this.labels = labels;
        }

        public int getNodes() { return nodes; }
        public void setNodes(int nodes) { this.nodes = nodes; }

        public int getEdges() { return edges; }
        public void setEdges(int edges) { this.edges = edges; }

        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
    }

    /**
     * Results for a single benchmark scenario.
     */
    public static class ScenarioResult {
        private String name;
        private String description;
        private String query;
        private int vectorOnlyChunks;
        private int knowledgeModelChunks;
        private LatencyStats vectorLatencyMs;
        private LatencyStats kmLatencyMs;
        private int graphNodesTraversed;
        private List<String> patternsExecuted;
        private long totalTimeMs;

        public ScenarioResult(String name) {
            this.name = name;
            this.patternsExecuted = new ArrayList<>();
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }

        public int getVectorOnlyChunks() { return vectorOnlyChunks; }
        public void setVectorOnlyChunks(int vectorOnlyChunks) { this.vectorOnlyChunks = vectorOnlyChunks; }

        public int getKnowledgeModelChunks() { return knowledgeModelChunks; }
        public void setKnowledgeModelChunks(int knowledgeModelChunks) { this.knowledgeModelChunks = knowledgeModelChunks; }

        public LatencyStats getVectorLatencyMs() { return vectorLatencyMs; }
        public void setVectorLatencyMs(LatencyStats vectorLatencyMs) { this.vectorLatencyMs = vectorLatencyMs; }

        public LatencyStats getKmLatencyMs() { return kmLatencyMs; }
        public void setKmLatencyMs(LatencyStats kmLatencyMs) { this.kmLatencyMs = kmLatencyMs; }

        public int getGraphNodesTraversed() { return graphNodesTraversed; }
        public void setGraphNodesTraversed(int graphNodesTraversed) { this.graphNodesTraversed = graphNodesTraversed; }

        public List<String> getPatternsExecuted() { return patternsExecuted; }
        public void setPatternsExecuted(List<String> patternsExecuted) { this.patternsExecuted = patternsExecuted; }

        public long getTotalTimeMs() { return totalTimeMs; }
        public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }
    }

    /**
     * Latency statistics (p50, p95, p99).
     */
    public static class LatencyStats {
        private long p50;
        private long p95;
        private long p99;
        private long min;
        private long max;
        private double avg;

        public LatencyStats() {}

        public LatencyStats(long p50, long p95, long p99) {
            this.p50 = p50;
            this.p95 = p95;
            this.p99 = p99;
        }

        /**
         * Compute latency statistics from raw measurements.
         */
        public static LatencyStats fromMeasurements(List<Long> measurements) {
            if (measurements.isEmpty()) {
                return new LatencyStats(0, 0, 0);
            }

            List<Long> sorted = measurements.stream().sorted().collect(Collectors.toList());
            int size = sorted.size();

            LatencyStats stats = new LatencyStats();
            stats.p50 = sorted.get((int) (size * 0.50));
            stats.p95 = sorted.get(Math.min((int) (size * 0.95), size - 1));
            stats.p99 = sorted.get(Math.min((int) (size * 0.99), size - 1));
            stats.min = sorted.get(0);
            stats.max = sorted.get(size - 1);
            stats.avg = measurements.stream().mapToLong(l -> l).average().orElse(0);

            return stats;
        }

        // Getters and setters
        public long getP50() { return p50; }
        public void setP50(long p50) { this.p50 = p50; }

        public long getP95() { return p95; }
        public void setP95(long p95) { this.p95 = p95; }

        public long getP99() { return p99; }
        public void setP99(long p99) { this.p99 = p99; }

        public long getMin() { return min; }
        public void setMin(long min) { this.min = min; }

        public long getMax() { return max; }
        public void setMax(long max) { this.max = max; }

        public double getAvg() { return avg; }
        public void setAvg(double avg) { this.avg = avg; }
    }

    /**
     * Summary statistics across all scenarios.
     */
    public static class Summary {
        private double avgChunkImprovement;
        private double avgLatencyOverhead;
        private long totalBenchmarkTimeMs;

        public Summary() {}

        public Summary(double avgChunkImprovement, double avgLatencyOverhead, long totalBenchmarkTimeMs) {
            this.avgChunkImprovement = avgChunkImprovement;
            this.avgLatencyOverhead = avgLatencyOverhead;
            this.totalBenchmarkTimeMs = totalBenchmarkTimeMs;
        }

        public double getAvgChunkImprovement() { return avgChunkImprovement; }
        public void setAvgChunkImprovement(double avgChunkImprovement) { this.avgChunkImprovement = avgChunkImprovement; }

        public double getAvgLatencyOverhead() { return avgLatencyOverhead; }
        public void setAvgLatencyOverhead(double avgLatencyOverhead) { this.avgLatencyOverhead = avgLatencyOverhead; }

        public long getTotalBenchmarkTimeMs() { return totalBenchmarkTimeMs; }
        public void setTotalBenchmarkTimeMs(long totalBenchmarkTimeMs) { this.totalBenchmarkTimeMs = totalBenchmarkTimeMs; }
    }
}
