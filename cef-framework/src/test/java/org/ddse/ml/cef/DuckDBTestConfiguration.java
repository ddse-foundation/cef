package org.ddse.ml.cef;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import javax.sql.DataSource;

import org.ddse.ml.cef.mcp.McpContextTool;
import org.springframework.context.annotation.Import;

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

    @Bean
    public DataSource dataSource() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource();
        dataSource.setDriverClassName("org.duckdb.DuckDBDriver");
        dataSource.setUrl("jdbc:duckdb:");
        dataSource.setSuppressClose(true); // Keep connection open
        return dataSource;
    }

    @Bean
    public org.ddse.ml.cef.repository.ChunkRepository chunkRepository() {
        return org.mockito.Mockito.mock(org.ddse.ml.cef.repository.ChunkRepository.class);
    }
}
