package org.ddse.ml.cef.benchmark.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Writes benchmark results to JSON and Markdown files.
 * JSON output is designed for consumption by Python evaluation scripts.
 *
 * @author mrmanna
 * @since v0.6
 */
public class BenchmarkResultWriter {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkResultWriter.class);
    
    private final ObjectMapper objectMapper;
    private final BenchmarkConfig config;

    public BenchmarkResultWriter(BenchmarkConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Write benchmark results to files.
     *
     * @param result The benchmark result to write
     * @throws IOException if file writing fails
     */
    public void writeResults(BenchmarkResult result) throws IOException {
        // Ensure output directory exists
        Path outputDir = Paths.get(config.getOutputDir(), result.getBackend());
        Files.createDirectories(outputDir);

        // Compute summary before writing
        result.computeSummary();

        // Write JSON for Python scripts
        if (config.isGenerateJson()) {
            Path jsonPath = outputDir.resolve(result.getDataset() + "_results.json");
            objectMapper.writeValue(jsonPath.toFile(), result);
            logger.info("JSON results written to: {}", jsonPath);
        }

        // Write Markdown report
        if (config.isGenerateMarkdown()) {
            Path mdPath = outputDir.resolve(result.getDataset() + "_report.md");
            writeMarkdownReport(result, mdPath);
            logger.info("Markdown report written to: {}", mdPath);
        }
    }

    /**
     * Write a Markdown report for human consumption.
     */
    private void writeMarkdownReport(BenchmarkResult result, Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            // Header
            writer.printf("# CEF Benchmark Report: %s Dataset%n", capitalize(result.getDataset()));
            writer.println();
            writer.printf("**Backend:** %s%n", result.getBackend());
            writer.printf("**Generated:** %s%n", result.getTimestamp().toString());
            writer.println();
            writer.println("---");
            writer.println();

            // Dataset Statistics
            if (result.getDatasetStats() != null) {
                writer.println("## Dataset Statistics");
                writer.println();
                writer.printf("- **Nodes:** %,d%n", result.getDatasetStats().getNodes());
                writer.printf("- **Edges:** %,d%n", result.getDatasetStats().getEdges());
                writer.printf("- **Labels:** %s%n", String.join(", ", result.getDatasetStats().getLabels()));
                writer.println();
            }

            // Scenario Results
            writer.println("## Scenario Results");
            writer.println();

            for (BenchmarkResult.ScenarioResult scenario : result.getScenarios()) {
                writeScenarioMarkdown(writer, scenario);
            }

            // Summary
            if (result.getSummary() != null) {
                writer.println("## Summary");
                writer.println();
                writer.println("| Metric | Value |");
                writer.println("|--------|-------|");
                writer.printf("| Avg Chunk Improvement | %.1f%% |%n", result.getSummary().getAvgChunkImprovement());
                writer.printf("| Avg Latency Overhead | %.1f%% |%n", result.getSummary().getAvgLatencyOverhead());
                writer.printf("| Total Benchmark Time | %,d ms |%n", result.getSummary().getTotalBenchmarkTimeMs());
                writer.println();
            }
        }
    }

    /**
     * Write a single scenario result to Markdown.
     */
    private void writeScenarioMarkdown(PrintWriter writer, BenchmarkResult.ScenarioResult scenario) {
        writer.printf("### %s%n", scenario.getName());
        writer.println();
        
        if (scenario.getDescription() != null) {
            writer.printf("**Objective:** %s%n", scenario.getDescription());
            writer.println();
        }
        
        if (scenario.getQuery() != null) {
            writer.printf("**Query:** \"%s\"%n", scenario.getQuery());
            writer.println();
        }

        // Results table
        writer.println("| Metric | Vector-Only | Knowledge Model | Improvement |");
        writer.println("|--------|-------------|-----------------|-------------|");

        // Chunks
        int chunkDiff = scenario.getKnowledgeModelChunks() - scenario.getVectorOnlyChunks();
        double chunkPct = scenario.getVectorOnlyChunks() > 0 
                ? ((double) chunkDiff / scenario.getVectorOnlyChunks()) * 100 
                : 0;
        writer.printf("| Chunks Retrieved | %d | %d | %+d (%.0f%%) |%n",
                scenario.getVectorOnlyChunks(),
                scenario.getKnowledgeModelChunks(),
                chunkDiff,
                chunkPct);

        // Latency
        if (scenario.getVectorLatencyMs() != null && scenario.getKmLatencyMs() != null) {
            writer.printf("| Latency (p50) | %d ms | %d ms | %+d ms |%n",
                    scenario.getVectorLatencyMs().getP50(),
                    scenario.getKmLatencyMs().getP50(),
                    scenario.getKmLatencyMs().getP50() - scenario.getVectorLatencyMs().getP50());
            writer.printf("| Latency (p95) | %d ms | %d ms | %+d ms |%n",
                    scenario.getVectorLatencyMs().getP95(),
                    scenario.getKmLatencyMs().getP95(),
                    scenario.getKmLatencyMs().getP95() - scenario.getVectorLatencyMs().getP95());
        }

        // Graph traversal stats
        if (scenario.getGraphNodesTraversed() > 0) {
            writer.printf("| Graph Nodes Traversed | - | %d | - |%n", scenario.getGraphNodesTraversed());
        }

        writer.println();

        // Patterns executed
        if (scenario.getPatternsExecuted() != null && !scenario.getPatternsExecuted().isEmpty()) {
            writer.println("**Patterns Executed:**");
            for (String pattern : scenario.getPatternsExecuted()) {
                writer.printf("- `%s`%n", pattern);
            }
            writer.println();
        }

        writer.println("---");
        writer.println();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
