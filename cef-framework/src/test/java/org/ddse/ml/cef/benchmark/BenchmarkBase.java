package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.DuckDBTestConfiguration;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.base.MedicalDataTestBase;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.repository.postgres.ChunkRepository;
import org.ddse.ml.cef.repository.postgres.EdgeRepository;
import org.ddse.ml.cef.repository.postgres.NodeRepository;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SpringBootTest(classes = DuckDBTestConfiguration.class, properties = "spring.main.allow-bean-definition-overriding=true")
@Import({ VllmTestConfiguration.class })
@ActiveProfiles({ "vllm-integration", "duckdb" })
public abstract class BenchmarkBase extends MedicalDataTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(BenchmarkBase.class);

    @Autowired
    protected KnowledgeRetriever retriever;

    /**
     * Metrics collected during benchmark execution.
     */
    protected static class BenchmarkMetrics {
        String approach;
        long latencyMs;
        int chunksRetrieved;
        List<String> chunkSummaries = List.of();

        public BenchmarkMetrics(String approach) {
            this.approach = approach;
        }
    }

    protected void runScenario(PrintWriter writer, String title, String description, String query,
            String... graphHints) {
        logger.info("=== Running Scenario: {} ===", title);

        writer.println("## " + title);
        writer.println();
        writer.println("**Objective:** " + description);
        writer.println();
        writer.println("**Query:** \"" + query + "\"");
        writer.println();

        // 1. Run Vector Only (Naive RAG)
        BenchmarkMetrics vectorMetrics = runVectorOnly(query);
        logger.info("Vector-Only: Latency: {}ms, Chunks: {}",
                vectorMetrics.latencyMs, vectorMetrics.chunksRetrieved);

        // 2. Run Knowledge Model (Graph RAG)
        BenchmarkMetrics kmMetrics = runKnowledgeModel(query, graphHints);
        logger.info("Knowledge Model: Latency: {}ms, Chunks: {}",
                kmMetrics.latencyMs, kmMetrics.chunksRetrieved);

        // Write comparison table
        writeComparisonTable(writer, vectorMetrics, kmMetrics);

        // Write analysis
        writeAnalysis(writer, vectorMetrics, kmMetrics);

        writer.println("---");
        writer.println();
    }

    /**
     * Run vector-only retrieval (Naive RAG baseline).
     */
    private BenchmarkMetrics runVectorOnly(String query) {
        BenchmarkMetrics metrics = new BenchmarkMetrics("Vector-Only (Naive RAG)");

        // Build retrieval request without graph query
        RetrievalRequest request = RetrievalRequest.builder()
                .query(query)
                .topK(5)
                .build();

        long startTime = System.currentTimeMillis();
        RetrievalResult result = retriever.retrieve(request).block();
        metrics.latencyMs = System.currentTimeMillis() - startTime;

        if (result != null) {
            metrics.chunksRetrieved = result.getChunks() != null ? result.getChunks().size() : 0;
            metrics.chunkSummaries = summarizeChunks(result);
        }

        return metrics;
    }

    /**
     * Run knowledge model retrieval (Graph RAG with traversal hints).
     */
    private BenchmarkMetrics runKnowledgeModel(String query, String[] graphHints) {
        BenchmarkMetrics metrics = new BenchmarkMetrics("Knowledge Model (Graph RAG)");

        // Build graph query from query text and hints
        GraphQuery graphQuery = buildGraphQuery(query, graphHints);

        // Build retrieval request with graph query
        RetrievalRequest request = RetrievalRequest.builder()
                .query(query)
                .graphQuery(graphQuery)
                .topK(5)
                .maxGraphNodes(50)
                .build();

        long startTime = System.currentTimeMillis();
        RetrievalResult result = retriever.retrieve(request).block();
        metrics.latencyMs = System.currentTimeMillis() - startTime;

        if (result != null) {
            metrics.chunksRetrieved = result.getChunks() != null ? result.getChunks().size() : 0;
            metrics.chunkSummaries = summarizeChunks(result);
        }

        return metrics;
    }

    /**
     * Build GraphQuery from query text and hints array.
     * 
     * Implements ADR-002's Vector-First Resolution strategy with GraphPattern
     * support:
     * - Uses query text for semantic vector search (finds relevant chunks)
     * - Uses typeHint to filter entry nodes (prevents context explosion)
     * - Creates GraphPattern for structured multi-hop traversal
     * 
     * Format: [targetLabel, relationType1, relationType2, ...]
     * 
     * @param query      Semantic query text for vector-based entry point resolution
     * @param graphHints Array of graph navigation hints
     * @return GraphQuery with resolution target and graph patterns
     */
    private GraphQuery buildGraphQuery(String query, String[] graphHints) {
        if (graphHints == null || graphHints.length == 0) {
            return null;
        }

        // Use query for semantic matching, typeHint for filtering
        // This avoids retrieving all nodes of a type (e.g., all 150 patients)
        ResolutionTarget target = new ResolutionTarget(
                query, // description: rich semantic query for vector search
                graphHints.length > 0 ? graphHints[0] : null, // typeHint: entity type filter
                null // properties
        );

        // Build traversal hint for backward compatibility
        TraversalHint traversal = null;
        if (graphHints.length > 1) {
            List<String> relationTypes = Arrays.asList(Arrays.copyOfRange(graphHints, 1, graphHints.length));
            traversal = new TraversalHint(
                    3, // maxDepth
                    relationTypes,
                    null // direction (null = both)
            );
        }

        // Build GraphPattern from hints (NEW: structured pattern support)
        List<org.ddse.ml.cef.dto.GraphPattern> patterns = new ArrayList<>();
        if (graphHints.length > 1) {
            String sourceLabel = graphHints[0];

            // Detect if this is a chain pattern (supply chain cascade) or star pattern
            // (patient conditions)
            // Chain patterns: Location, Event, Product (single entity flowing through
            // supply chain)
            // Star patterns: Patient, Department (central entity with multiple direct
            // relationships)
            boolean isChainPattern = sourceLabel.equals("Location") || sourceLabel.equals("Event") ||
                    sourceLabel.equals("Product");

            if (isChainPattern) {
                // Create ONE multi-step sequential pattern for cascading traversal
                // Example: Location→LOCATED_IN→Vendor→SUPPLIED_BY→Material→COMPONENT_OF→Product
                List<org.ddse.ml.cef.dto.TraversalStep> steps = new ArrayList<>();
                for (int i = 1; i < graphHints.length; i++) {
                    steps.add(new org.ddse.ml.cef.dto.TraversalStep(
                            i == 1 ? sourceLabel : "*", // First step uses source label, rest use wildcard
                            graphHints[i],
                            "*",
                            i - 1));
                }

                patterns.add(org.ddse.ml.cef.dto.GraphPattern.multiHop(
                        "pattern-" + sourceLabel + "-chain",
                        steps,
                        "Multi-step chain pattern: " + sourceLabel + " cascade"));
            } else {
                // Create SEPARATE 1-hop patterns for star topology (existing logic)
                // All patterns start from graphHints[0] (e.g., Patient)
                // This matches graph structures where relations fan out from same source node
                for (int i = 1; i < graphHints.length; i++) {
                    String relationType = graphHints[i];
                    List<org.ddse.ml.cef.dto.TraversalStep> steps = List.of(
                            new org.ddse.ml.cef.dto.TraversalStep(
                                    sourceLabel,
                                    relationType,
                                    "*",
                                    0));

                    patterns.add(org.ddse.ml.cef.dto.GraphPattern.multiHop(
                            "pattern-" + sourceLabel + "-" + relationType,
                            steps,
                            "Single-hop pattern: " + sourceLabel + " --" + relationType + "--> *"));
                }
            }
        }

        return new GraphQuery(
                List.of(target),
                traversal,
                patterns, // NEW: patterns field
                null, // NEW: combinator field
                org.ddse.ml.cef.dto.RankingStrategy.HYBRID // NEW: rankingStrategy field
        );
    }

    /**
     * Extract a sample of context for display.
     */
    private List<String> summarizeChunks(RetrievalResult result) {
        if (result.getChunks() == null || result.getChunks().isEmpty()) {
            return List.of("(no chunks)");
        }

        List<String> summaries = new ArrayList<>();
        result.getChunks().forEach(chunk -> {
            String content = chunk.getContent() != null ? chunk.getContent().replace('\n', ' ') : "";
            if (content.length() > 160) {
                content = content.substring(0, 160) + "...";
            }
            summaries.add(String.format("%s | node=%s | %s",
                    chunk.getId(),
                    chunk.getLinkedNodeId(),
                    content));
        });
        return summaries;
    }

    /**
     * Write comparison table for metrics.
     */
    private void writeComparisonTable(PrintWriter writer, BenchmarkMetrics vectorMetrics,
            BenchmarkMetrics kmMetrics) {
        writer.println("### Results Comparison");
        writer.println();
        writer.println("| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |");
        writer.println("|--------|------------------------|----------------------------|-------------|");

        // Latency
        double latencyImprovement = calculateImprovement(vectorMetrics.latencyMs, kmMetrics.latencyMs);
        String latencyArrow = latencyImprovement > 0 ? "↑" : "↓";
        writer.printf("| **Latency** | %dms | %dms | %s%.1f%% |%n",
                vectorMetrics.latencyMs, kmMetrics.latencyMs, latencyArrow, Math.abs(latencyImprovement));

        // Chunks
        writer.printf("| **Chunks Retrieved** | %d | %d | %s |%n",
                vectorMetrics.chunksRetrieved, kmMetrics.chunksRetrieved,
                kmMetrics.chunksRetrieved > vectorMetrics.chunksRetrieved
                        ? "+" + (kmMetrics.chunksRetrieved - vectorMetrics.chunksRetrieved)
                        : "-");
        writer.println();
    }

    /**
     * Write analysis section.
     */
    private void writeAnalysis(PrintWriter writer, BenchmarkMetrics vectorMetrics,
            BenchmarkMetrics kmMetrics) {
        writer.println("### Raw Results");
        writer.println();

        writer.println("**Vector-Only (Naive RAG):**");
        writer.println("```");
        vectorMetrics.chunkSummaries.forEach(writer::println);
        writer.println("```");
        writer.println();

        writer.println("**Knowledge Model (Graph RAG):**");
        writer.println("```");
        kmMetrics.chunkSummaries.forEach(writer::println);
        writer.println("```");
        writer.println();
    }

    /**
     * Calculate percentage improvement (negative means slower).
     */
    private double calculateImprovement(long baseline, long current) {
        if (baseline == 0)
            return 0;
        return ((double) (current - baseline) / baseline) * 100;
    }

    protected void writeHeader(PrintWriter writer, String title, String domain, String description) {
        writer.println("# " + title);
        writer.println();
        writer.println("**Domain:** " + domain);
        writer.println();
        writer.println("**Date:** " + Instant.now());
        writer.println();
        writer.println(description);
        writer.println();
        writer.println("---");
        writer.println();
    }

    @TestConfiguration
    static class Config {

        @Bean(name = "knowledgeIndexer")
        KnowledgeIndexer knowledgeIndexer(
                GraphStore graphStore,
                @Autowired(required = false) NodeRepository nodeRepository,
                @Autowired(required = false) EdgeRepository edgeRepository,
                ChunkStore chunkStore,
                EmbeddingModel embeddingModel,
                @Autowired(required = false) ChunkRepository chunkRepository) {
            // ChunkRepository is optional for DuckDB profile; chunkStore provides
            // persistence.
            return new DefaultKnowledgeIndexer(graphStore, nodeRepository, edgeRepository, chunkStore,
                    embeddingModel);
        }
    }
}
