package org.ddse.ml.cef;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import javax.sql.DataSource;

import org.ddse.ml.cef.mcp.McpContextTool;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.indexer.DefaultKnowledgeIndexer;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.repository.postgres.NodeRepository;
import org.ddse.ml.cef.repository.postgres.EdgeRepository;
import org.ddse.ml.cef.graph.GraphStore;
import org.springframework.context.annotation.Import;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * Test configuration for DuckDB repository tests.
 * Uses pure JDBC DuckDB for both graph data and vector data.
 * No R2DBC - JDBC with reactive wrappers.
 * 
 * @author mrmanna
 */
@SpringBootApplication(exclude = {
                org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration.class,
                org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration.class,
                org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration.class
})
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org\\.ddse\\.ml\\.cef\\.mcp\\..*"))
@Import(McpContextTool.class)
@Profile("duckdb")
public class DuckDBTestConfiguration {

        @Bean
        public DataSourceInitializer dataSourceInitializer(DataSource dataSource) {
                DataSourceInitializer initializer = new DataSourceInitializer();
                initializer.setDataSource(dataSource);
                initializer.setDatabasePopulator(new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                                new ClassPathResource("schema-duckdb-benchmark.sql")));
                return initializer;
        }

        /**
         * Real Ollama embedding model for tests with actual 768-dim embeddings.
         */
        @Bean
        public EmbeddingModel embeddingModel(
                        @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
                OllamaApi ollamaApi = new OllamaApi(baseUrl);
                return OllamaEmbeddingModel.builder()
                                .withOllamaApi(ollamaApi)
                                .withDefaultOptions(OllamaOptions.builder()
                                                .withModel("nomic-embed-text")
                                                .build())
                                .build();
        }

        /**
         * KnowledgeIndexer bean for dual persistence (graph + chunks).
         */
        @Bean
        public KnowledgeIndexer knowledgeIndexer(
                        GraphStore graphStore,
                        @Autowired(required = false) NodeRepository nodeRepository,
                        @Autowired(required = false) EdgeRepository edgeRepository,
                        ChunkStore chunkStore,
                        EmbeddingModel embeddingModel) {
                return new DefaultKnowledgeIndexer(graphStore, nodeRepository, edgeRepository,
                                chunkStore, embeddingModel);
        }
}
