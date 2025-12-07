package org.ddse.ml.cef.benchmark.core;

/**
 * Configuration for benchmark execution.
 * Centralized location for all benchmark parameters.
 *
 * @author mrmanna
 * @since v0.6
 */
public class BenchmarkConfig {

    /** Number of warmup iterations before measuring */
    private int warmupIterations = 3;

    /** Number of measured iterations for latency statistics */
    private int measuredIterations = 10;

    /** TopK for vector search */
    private int topK = 5;

    /** Maximum graph nodes to traverse */
    private int maxGraphNodes = 50;

    /** Maximum traversal depth */
    private int maxDepth = 3;

    /** Output directory for results */
    private String outputDir = "src/test/resources/scripts/results";

    /** Whether to generate JSON output for Python scripts */
    private boolean generateJson = true;

    /** Whether to generate Markdown report */
    private boolean generateMarkdown = true;

    // Singleton instance with defaults
    private static final BenchmarkConfig DEFAULT = new BenchmarkConfig();

    public static BenchmarkConfig defaults() {
        return DEFAULT;
    }

    // Builder pattern
    public BenchmarkConfig withWarmupIterations(int warmupIterations) {
        this.warmupIterations = warmupIterations;
        return this;
    }

    public BenchmarkConfig withMeasuredIterations(int measuredIterations) {
        this.measuredIterations = measuredIterations;
        return this;
    }

    public BenchmarkConfig withTopK(int topK) {
        this.topK = topK;
        return this;
    }

    public BenchmarkConfig withMaxGraphNodes(int maxGraphNodes) {
        this.maxGraphNodes = maxGraphNodes;
        return this;
    }

    public BenchmarkConfig withMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public BenchmarkConfig withOutputDir(String outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public BenchmarkConfig withGenerateJson(boolean generateJson) {
        this.generateJson = generateJson;
        return this;
    }

    public BenchmarkConfig withGenerateMarkdown(boolean generateMarkdown) {
        this.generateMarkdown = generateMarkdown;
        return this;
    }

    // Getters
    public int getWarmupIterations() {
        return warmupIterations;
    }

    public int getMeasuredIterations() {
        return measuredIterations;
    }

    public int getTopK() {
        return topK;
    }

    public int getMaxGraphNodes() {
        return maxGraphNodes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public boolean isGenerateJson() {
        return generateJson;
    }

    public boolean isGenerateMarkdown() {
        return generateMarkdown;
    }
}
