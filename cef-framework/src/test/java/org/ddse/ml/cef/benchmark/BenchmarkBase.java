package org.ddse.ml.cef.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.DuckDBTestConfiguration;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.mcp.McpContextTool;
import org.ddse.ml.cef.parser.impl.CsvParser;
import org.ddse.ml.cef.repository.ChunkRepository;
import org.ddse.ml.cef.repository.ChunkStore;
import org.ddse.ml.cef.repository.EdgeRepository;
import org.ddse.ml.cef.repository.NodeRepository;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.storage.GraphStore;
import org.ddse.ml.cef.storage.r2dbc.R2dbcGraphStore;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ai.model.function.FunctionCallback;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = DuckDBTestConfiguration.class, properties = "spring.main.allow-bean-definition-overriding=true")
@Import({ VllmTestConfiguration.class })
@ActiveProfiles({ "vllm-integration", "duckdb" })
public abstract class BenchmarkBase {

    protected static final Logger logger = LoggerFactory.getLogger(BenchmarkBase.class);

    @Autowired
    protected McpContextTool mcpTool;

    @Autowired
    protected GraphStore graphStore;

    @Autowired
    protected ChunkStore chunkStore;

    @Autowired
    protected CsvParser csvParser;

    @Autowired
    protected EmbeddingModel embeddingModel;

    @Autowired
    protected ChatClient.Builder chatClientBuilder;

    @Autowired
    protected org.ddse.ml.cef.indexer.KnowledgeIndexer indexer;

    protected ChatClient chatClient;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    @TestConfiguration
    static class Config {
        // Framework auto-configuration already provides all necessary beans
        // DuckDBTestConfiguration provides database setup
        // Only override what's absolutely necessary for tests
    }

    @BeforeEach
    void setupBase() {
        this.chatClient = chatClientBuilder.build();
        graphStore.clear().block();
        chunkStore.deleteAll().block();
    }

    protected void runScenario(PrintWriter writer, String title, String description, String query,
            String... graphHints) {
        writer.println("## " + title);
        writer.println("**Clinical Question:** \"" + description + "\"");
        writer.println();

        // 1. Run Vector Only (Naive RAG) - Get LLM final output
        long vectorStart = System.currentTimeMillis();
        String vectorLLMOutput = executeLLMWithVectorOnly(description);
        long vectorTime = System.currentTimeMillis() - vectorStart;

        // 2. Run Knowledge Model (Graph+Vector Hybrid) - Get LLM final output
        long kmStart = System.currentTimeMillis();
        String kmLLMOutput = executeLLMWithKnowledgeModel(query);
        long kmTime = System.currentTimeMillis() - kmStart;

        // Display side-by-side outputs
        writer.println("### Knowledge Model Output (Graph + Vector)");
        writer.println("**Duration:** " + kmTime + "ms");
        writer.println();
        writer.println(kmLLMOutput);
        writer.println();

        writer.println("### Vector-Only Output (Baseline RAG)");
        writer.println("**Duration:** " + vectorTime + "ms");
        writer.println();
        writer.println(vectorLLMOutput);
        writer.println();

        writer.println("---");
        writer.println();
    }

    private String executeLLMWithVectorOnly(String query) {
        // Pure vector search
        float[] queryVector = embeddingModel.embed(query);
        List<Chunk> chunks = chunkStore.findTopKSimilar(queryVector, 10).collectList().block();

        // Format context for LLM
        StringBuilder context = new StringBuilder();
        context.append("Based on the following clinical notes, answer the question.\n\n");
        context.append("Clinical Notes:\n");
        if (chunks != null) {
            for (int i = 0; i < chunks.size(); i++) {
                context.append((i + 1)).append(". ").append(chunks.get(i).getContent()).append("\n\n");
            }
        }

        // Get LLM answer
        String prompt = context + "\nQuestion: " + query + "\n\nAnswer:";
        return chatClient.prompt(prompt).call().content();
    }

    protected String executeLLMWithKnowledgeModel(String query) {
        // Use MCP tool (Knowledge Model with graph+vector) via native tool calling
        // The LLM will decide to call the tool, execute it, and use the result to
        // answer.
        logger.info("=== [Benchmark] Sending Query to LLM ===");
        logger.info("Query: {}", query);

        String response = chatClient.prompt(query)
                .functions(mcpTool)
                .call()
                .content();

        logger.info("=== [Benchmark] LLM Initial Response ===");
        logger.info("Response: {}", response);

        return response;
    }

    private void evaluateOutputs(PrintWriter writer, String kmOutput, String vectorOutput, String groundTruth) {
        // Simple evaluation - count matches
        // In production, this would use NLP/LLM-based evaluation
        writer.println("**Note:** Evaluation metrics require manual review or automated NLP scoring.");
        writer.println("- **Ground Truth:** " + groundTruth);
        writer.println("- **KM Output Length:** " + kmOutput.length() + " chars");
        writer.println("- **Vector Output Length:** " + vectorOutput.length() + " chars");

        // Check if ground truth keywords appear in outputs
        String[] keywords = groundTruth.toLowerCase().split("\\s+");
        long kmMatches = java.util.Arrays.stream(keywords)
                .filter(kw -> kmOutput.toLowerCase().contains(kw))
                .count();
        long vectorMatches = java.util.Arrays.stream(keywords)
                .filter(kw -> vectorOutput.toLowerCase().contains(kw))
                .count();

        writer.println("- **KM Keyword Coverage:** " + kmMatches + "/" + keywords.length);
        writer.println("- **Vector Keyword Coverage:** " + vectorMatches + "/" + keywords.length);
    }

    protected String formatVectorResult(List<Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Context Retrieval Result\n\n");
        sb.append("**Strategy:** VECTOR_ONLY\n");
        sb.append("**Results:** ").append(chunks != null ? chunks.size() : 0).append(" chunks\n\n");
        sb.append("## Vector Context\n");
        if (chunks != null) {
            for (Chunk c : chunks) {
                sb.append("- ").append(c.getContent()).append("\n");
            }
        }
        return sb.toString();
    }

    protected void writeHeader(PrintWriter writer, String title, String domain, String description) {
        writer.println("# " + title);
        writer.println("**Domain:** " + domain);
        writer.println("**Date:** " + new Date());
        writer.println();
        writer.println(description);
        writer.println();
    }

}
