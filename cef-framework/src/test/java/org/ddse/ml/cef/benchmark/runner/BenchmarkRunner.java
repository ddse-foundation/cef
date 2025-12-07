package org.ddse.ml.cef.benchmark.runner;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.benchmark.core.*;
import org.ddse.ml.cef.benchmark.dataset.MedicalDataset;
import org.ddse.ml.cef.benchmark.dataset.SapDataset;
import org.ddse.ml.cef.benchmark.scenario.MedicalScenarios;
import org.ddse.ml.cef.benchmark.scenario.SapScenarios;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.*;
import org.ddse.ml.cef.graph.GraphStore;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Executes benchmarks against any GraphStore implementation.
 * 
 * <p><b>Architecture:</b> Benchmarks use Direct Mode: Query → KnowledgeRetriever directly.</p>
 *
 * @author mrmanna
 * @since v0.6
 */
public class BenchmarkRunner {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final String backendName;
    private final GraphStore graphStore;
    private final ChunkStore chunkStore;
    private final KnowledgeIndexer indexer;
    private final KnowledgeRetriever retriever;
    private final BenchmarkConfig config;

    /**
     * Constructor for direct mode.
     */
    public BenchmarkRunner(String backendName,
                           GraphStore graphStore,
                           ChunkStore chunkStore,
                           KnowledgeIndexer indexer,
                           KnowledgeRetriever retriever,
                           BenchmarkConfig config) {
        this.backendName = backendName;
        this.graphStore = graphStore;
        this.chunkStore = chunkStore;
        this.indexer = indexer;
        this.retriever = retriever;
        this.config = config;
        
        logger.info("BenchmarkRunner initialized in DIRECT MODE");
    }

    /**
     * Run all medical benchmarks.
     */
    public BenchmarkResult runMedicalBenchmarks(MedicalDataset dataset) {
        logger.info("Running medical benchmarks on {} backend", backendName);

        // Ingest data
        ingestData(dataset.getNodes(), dataset.getEdges(), dataset.getChunks());

        // Create result container
        BenchmarkResult result = new BenchmarkResult(backendName, "medical");
        result.setDatasetStats(new BenchmarkResult.DatasetStats(
                dataset.getNodeCount(),
                dataset.getEdgeCount(),
                dataset.getLabels()
        ));

        // Run each scenario
        for (MedicalScenarios.Scenario scenario : MedicalScenarios.ALL) {
            BenchmarkResult.ScenarioResult scenarioResult = runScenario(
                    scenario.getName(),
                    scenario.getDescription(),
                    scenario.getQuery(),
                    scenario.getGraphHints()
            );
            result.addScenario(scenarioResult);
        }

        return result;
    }

    /**
     * Run all SAP benchmarks.
     */
    public BenchmarkResult runSapBenchmarks(SapDataset dataset) {
        logger.info("Running SAP benchmarks on {} backend", backendName);

        // Ingest data
        ingestData(dataset.getNodes(), dataset.getEdges(), dataset.getChunks());

        // Create result container
        BenchmarkResult result = new BenchmarkResult(backendName, "sap");
        result.setDatasetStats(new BenchmarkResult.DatasetStats(
                dataset.getNodeCount(),
                dataset.getEdgeCount(),
                dataset.getLabels()
        ));

        // Run each scenario
        for (SapScenarios.Scenario scenario : SapScenarios.ALL) {
            BenchmarkResult.ScenarioResult scenarioResult = runScenario(
                    scenario.getName(),
                    scenario.getDescription(),
                    scenario.getQuery(),
                    scenario.getGraphHints()
            );
            result.addScenario(scenarioResult);
        }

        return result;
    }

    /**
     * Ingest data into the graph store and chunk store.
     */
    private void ingestData(List<Node> nodes, List<Edge> edges, List<Chunk> chunks) {
        logger.info("Ingesting data: {} nodes, {} edges, {} chunks", nodes.size(), edges.size(), chunks.size());
        long startTime = System.currentTimeMillis();

        // Clear existing data
        graphStore.clear().block();
        chunkStore.deleteAll().block();

        // Ingest nodes
        for (Node node : nodes) {
            node.setNew(true);
            indexer.indexNode(node).block();
        }

        // Ingest edges
        for (Edge edge : edges) {
            edge.setNew(true);
            indexer.indexEdge(edge).block();
        }

        // Ingest chunks
        for (Chunk chunk : chunks) {
            chunk.setNew(true);
            indexer.indexChunk(chunk).block();
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Data ingestion completed in {}ms", duration);
    }

    /**
     * Run a single benchmark scenario.
     */
    private BenchmarkResult.ScenarioResult runScenario(String name, String description, 
                                                        String query, String[] graphHints) {
        logger.info("Running scenario: {}", name);
        long scenarioStartTime = System.currentTimeMillis();

        BenchmarkResult.ScenarioResult result = new BenchmarkResult.ScenarioResult(name);
        result.setDescription(description);
        result.setQuery(query);

        // Warmup
        for (int i = 0; i < config.getWarmupIterations(); i++) {
            runVectorOnly(query);
            runKnowledgeModel(query, graphHints);
        }

        // Measure vector-only
        List<Long> vectorLatencies = new ArrayList<>();
        int vectorChunks = 0;
        for (int i = 0; i < config.getMeasuredIterations(); i++) {
            long start = System.currentTimeMillis();
            RetrievalResult vectorResult = runVectorOnly(query);
            vectorLatencies.add(System.currentTimeMillis() - start);
            if (vectorResult != null && vectorResult.getChunks() != null) {
                vectorChunks = vectorResult.getChunks().size();
            }
        }

        // Measure knowledge model
        List<Long> kmLatencies = new ArrayList<>();
        int kmChunks = 0;
        int graphNodes = 0;
        for (int i = 0; i < config.getMeasuredIterations(); i++) {
            long start = System.currentTimeMillis();
            RetrievalResult kmResult = runKnowledgeModel(query, graphHints);
            kmLatencies.add(System.currentTimeMillis() - start);
            if (kmResult != null) {
                if (kmResult.getChunks() != null) {
                    kmChunks = kmResult.getChunks().size();
                }
                if (kmResult.getNodes() != null) {
                    graphNodes = kmResult.getNodes().size();
                }
            }
        }

        // Record results
        result.setVectorOnlyChunks(vectorChunks);
        result.setKnowledgeModelChunks(kmChunks);
        result.setVectorLatencyMs(BenchmarkResult.LatencyStats.fromMeasurements(vectorLatencies));
        result.setKmLatencyMs(BenchmarkResult.LatencyStats.fromMeasurements(kmLatencies));
        result.setGraphNodesTraversed(graphNodes);
        result.setPatternsExecuted(buildPatternDescriptions(graphHints));
        result.setTotalTimeMs(System.currentTimeMillis() - scenarioStartTime);

        logger.info("Scenario {} complete: vectorChunks={}, kmChunks={}, p50Latency={}ms/{}ms",
                name, vectorChunks, kmChunks, 
                result.getVectorLatencyMs().getP50(), 
                result.getKmLatencyMs().getP50());

        return result;
    }

    /**
     * Run vector-only retrieval (Naive RAG baseline).
     * In Direct mode: calls retriever directly.
     */
    private RetrievalResult runVectorOnly(String query) {
        return runVectorOnlyDirect(query);
    }

    /**
     * Run knowledge model retrieval (Graph RAG).
     * In Direct mode: calls retriever directly with graph query.
     */
    private RetrievalResult runKnowledgeModel(String query, String[] graphHints) {
        return runKnowledgeModelDirect(query, graphHints);
    }

    // ========== Direct Mode Methods (For Advanced Debugging) ==========

    /**
     * Direct vector-only retrieval (bypasses LLM).
     */
    private RetrievalResult runVectorOnlyDirect(String query) {
        RetrievalRequest request = RetrievalRequest.builder()
                .query(query)
                .topK(config.getTopK())
                .build();

        return retriever.retrieve(request).block();
    }

    /**
     * Direct knowledge model retrieval (bypasses LLM).
     */
    private RetrievalResult runKnowledgeModelDirect(String query, String[] graphHints) {
        GraphQuery graphQuery = buildGraphQuery(query, graphHints);

        RetrievalRequest request = RetrievalRequest.builder()
                .query(query)
                .graphQuery(graphQuery)
                .topK(config.getTopK())
                .maxGraphNodes(config.getMaxGraphNodes())
                .build();

        return retriever.retrieve(request).block();
    }

    /**
     * Build GraphQuery from hints (same logic as original BenchmarkBase).
     */
    private GraphQuery buildGraphQuery(String query, String[] graphHints) {
        if (graphHints == null || graphHints.length == 0) {
            return null;
        }

        ResolutionTarget target = new ResolutionTarget(
                query,
                graphHints.length > 0 ? graphHints[0] : null,
                null
        );

        TraversalHint traversal = null;
        if (graphHints.length > 1) {
            List<String> relationTypes = Arrays.asList(Arrays.copyOfRange(graphHints, 1, graphHints.length));
            traversal = new TraversalHint(
                    config.getMaxDepth(),
                    relationTypes,
                    null
            );
        }

        // Build patterns
        List<GraphPattern> patterns = new ArrayList<>();
        if (graphHints.length > 1) {
            String sourceLabel = graphHints[0];
            for (int i = 1; i < graphHints.length; i++) {
                String relationType = graphHints[i];
                List<TraversalStep> steps = List.of(
                        new TraversalStep(sourceLabel, relationType, "*", 0)
                );
                patterns.add(GraphPattern.multiHop(
                        "pattern-" + sourceLabel + "-" + relationType,
                        steps,
                        "Single-hop pattern: " + sourceLabel + " --" + relationType + "--> *"
                ));
            }
        }

        return new GraphQuery(
                List.of(target),
                traversal,
                patterns,
                null,
                RankingStrategy.HYBRID
        );
    }

    /**
     * Build pattern descriptions for reporting.
     */
    private List<String> buildPatternDescriptions(String[] graphHints) {
        List<String> patterns = new ArrayList<>();
        if (graphHints != null && graphHints.length > 1) {
            String sourceLabel = graphHints[0];
            for (int i = 1; i < graphHints.length; i++) {
                patterns.add(sourceLabel + "→" + graphHints[i] + "→*");
            }
        }
        return patterns;
    }
}
