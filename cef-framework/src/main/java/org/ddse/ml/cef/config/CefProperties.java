package org.ddse.ml.cef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 *     store: jgrapht  # jgrapht | neo4j | tinkerpop
 *   vector:
 *     store: postgres  # postgres | duckdb | qdrant | pinecone
 *     dimension: 1536  # OpenAI default, 768 for nomic-embed-text
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
public class CefProperties {

    private GraphConfig graph = new GraphConfig();
    private VectorConfig vector = new VectorConfig();
    private McpConfig mcp = new McpConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();

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

    public static class GraphConfig {
        private String store = "jgrapht";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }
    }

    public static class VectorConfig {
        private String store = "postgres";
        private int dimension = 1536;

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
        private int maxTokenBudget = 4000;
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
        private int batchSize = 100;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
