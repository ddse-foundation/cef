package org.ddse.ml.cef.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration properties for CEF framework.
 * Maps to application.yml properties under 'cef' prefix.
 * 
 * Note: Embedding provider configuration is handled by Spring AI's native
 * configuration.
 * Use spring.ai.ollama or spring.ai.openai properties for embedding
 * configuration.
 * 
 * Example configuration:
 * 
 * <pre>
 * cef:
 *   graph:
 *     store: duckdb  # duckdb | in-memory | neo4j | pg-sql | pg-age
 *   vector:
 *     store: duckdb  # duckdb | postgresql | neo4j
 *     dimension: 768  # nomic-embed-text default
 *   mcp:
 *     required-fields:
 *       - textQuery
 *     # graphHints and semanticKeywords are optional by default
 *   embedding:
 *     batch-size: 100  # Batch size for CEF framework operations
 * 
 * # Spring AI handles embedding provider configuration
 * spring:
 *   ai:
 *     ollama:  # or openai
 *       base-url: http://localhost:11434
 *       embedding:
 *         options:
 *           model: nomic-embed-text:latest
 * </pre>
 *
 * @author mrmanna
 */
@ConfigurationProperties(prefix = "cef")
@Validated
public class CefProperties {

    @Valid
    @NotNull
    private GraphConfig graph = new GraphConfig();

    @Valid
    @NotNull
    private VectorConfig vector = new VectorConfig();

    @Valid
    @NotNull
    private McpConfig mcp = new McpConfig();

    @Valid
    @NotNull
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Valid
    @NotNull
    private LlmConfig llm = new LlmConfig();

    public GraphConfig getGraph() {
        return graph;
    }

    public void setGraph(GraphConfig graph) {
        this.graph = graph;
    }

    public VectorConfig getVector() {
        return vector;
    }

    public void setVector(VectorConfig vector) {
        this.vector = vector;
    }

    public McpConfig getMcp() {
        return mcp;
    }

    public void setMcp(McpConfig mcp) {
        this.mcp = mcp;
    }

    public EmbeddingConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingConfig embedding) {
        this.embedding = embedding;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public static class GraphConfig {
        /**
         * Graph store implementation: duckdb, in-memory, neo4j, pg-sql, pg-age.
         */
        @NotNull
        @Size(min = 1, max = 50)
        private String store = "duckdb";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }
    }

    public static class VectorConfig {
        /**
         * Vector store implementation: duckdb, in-memory, postgresql, neo4j.
         * <ul>
         *   <li><b>duckdb</b> - Embedded DuckDB with VSS extension (default)</li>
         *   <li><b>in-memory</b> - Zero-dependency, non-persistent, for dev/testing</li>
         *   <li><b>postgresql</b> - PostgreSQL with pgvector extension</li>
         *   <li><b>neo4j</b> - Neo4j with vector indexes (5.11+)</li>
         * </ul>
         */
        @NotNull
        @Size(min = 1, max = 50)
        private String store = "duckdb";

        /**
         * Embedding dimension. Common values:
         * - 768 for nomic-embed-text, sentence-transformers
         * - 1536 for OpenAI text-embedding-ada-002
         * - 3072 for OpenAI text-embedding-3-large
         */
        @Min(128)
        @Max(4096)
        private int dimension = 768;

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }
    }

    public static class McpConfig {
        private Set<String> requiredFields = new HashSet<>();

        /**
         * Maximum token budget for context assembly.
         * Prevents excessive context that could exceed LLM limits.
         */
        @Min(100)
        @Max(128000)
        private int maxTokenBudget = 4000;

        /**
         * Maximum graph nodes to include in context.
         * Prevents excessive graph traversals.
         */
        @Min(1)
        @Max(500)
        private int maxGraphNodes = 50;

        public McpConfig() {
            // Default: only textQuery is required
            requiredFields.add("textQuery");
        }

        public Set<String> getRequiredFields() {
            return requiredFields;
        }

        public void setRequiredFields(Set<String> requiredFields) {
            this.requiredFields = requiredFields;
        }

        public boolean isFieldRequired(String fieldName) {
            return requiredFields.contains(fieldName);
        }

        public int getMaxTokenBudget() {
            return maxTokenBudget;
        }

        public void setMaxTokenBudget(int maxTokenBudget) {
            this.maxTokenBudget = maxTokenBudget;
        }

        public int getMaxGraphNodes() {
            return maxGraphNodes;
        }

        public void setMaxGraphNodes(int maxGraphNodes) {
            this.maxGraphNodes = maxGraphNodes;
        }
    }

    public static class EmbeddingConfig {
        /**
         * Batch size for embedding operations.
         * Larger batches improve throughput but increase memory usage.
         */
        @Min(1)
        @Max(1000)
        private int batchSize = 100;

        /**
         * Maximum texts to embed in a single request to prevent timeouts.
         */
        @Min(1)
        @Max(500)
        private int maxTextsPerRequest = 100;

        /**
         * Cache embeddings to avoid redundant computation.
         */
        private boolean cacheEnabled = true;

        /**
         * Cache TTL in seconds.
         */
        @Min(60)
        @Max(86400)
        private int cacheTtlSeconds = 3600;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxTextsPerRequest() {
            return maxTextsPerRequest;
        }

        public void setMaxTextsPerRequest(int maxTextsPerRequest) {
            this.maxTextsPerRequest = maxTextsPerRequest;
        }

        public boolean isCacheEnabled() {
            return cacheEnabled;
        }

        public void setCacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
        }

        public int getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(int cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }
    }

    /**
     * LLM configuration for agentic retrieval.
     * When enabled, KnowledgeRetriever uses LLM with tool calling for intelligent retrieval.
     */
    public static class LlmConfig {
        /**
         * Enable LLM-powered retrieval.
         * When true, queries go through LLM which decides when/how to call retrieve_context tool.
         * When false (default), direct retrieval without LLM.
         */
        private boolean enabled = false;

        /**
         * System prompt for the LLM agent.
         */
        private String systemPrompt = """
            You are a helpful assistant with access to a knowledge retrieval tool.
            Use the retrieve_context tool to find information before answering questions.
            After retrieving context, synthesize a helpful answer based on the retrieved information.
            If no relevant information is found, say so clearly.
            """;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }
    }
}
