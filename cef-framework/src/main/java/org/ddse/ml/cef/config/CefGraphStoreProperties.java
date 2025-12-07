package org.ddse.ml.cef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for CEF Graph Store selection.
 * 
 * <p>Allows users to select the graph store implementation via application.yml:</p>
 * <pre>
 * cef:
 *   graph:
 *     store: duckdb | in-memory | neo4j | pg-sql | pg-age
 * </pre>
 * 
 * <h3>Available Stores:</h3>
 * <ul>
 *   <li><b>duckdb</b> - DuckDB SQL graph store, embedded single-file database (default)</li>
 *   <li><b>in-memory</b> - JGraphT in-memory store, for development and testing</li>
 *   <li><b>neo4j</b> - Native graph database, best for complex traversals (requires Neo4j service)</li>
 *   <li><b>pg-sql</b> - Pure PostgreSQL SQL with adjacency tables, maximum compatibility</li>
 *   <li><b>pg-age</b> - PostgreSQL with Apache AGE extension, Cypher queries on PostgreSQL</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
@ConfigurationProperties(prefix = "cef.graph")
public class CefGraphStoreProperties {

    /**
     * The graph store implementation to use.
     * Options: duckdb, in-memory, neo4j, pg-sql, pg-age
     * Default: duckdb
     */
    private StoreType store = StoreType.DUCKDB;

    /**
     * Neo4j-specific configuration.
     */
    private Neo4jConfig neo4j = new Neo4jConfig();

    /**
     * PostgreSQL-specific configuration (for pg-age and pg-sql).
     */
    private PostgresConfig postgres = new PostgresConfig();

    public StoreType getStore() {
        return store;
    }

    public void setStore(StoreType store) {
        this.store = store;
    }

    public Neo4jConfig getNeo4j() {
        return neo4j;
    }

    public void setNeo4j(Neo4jConfig neo4j) {
        this.neo4j = neo4j;
    }

    public PostgresConfig getPostgres() {
        return postgres;
    }

    public void setPostgres(PostgresConfig postgres) {
        this.postgres = postgres;
    }

    /**
     * Graph store type enumeration.
     */
    public enum StoreType {
        /**
         * Neo4j native graph database.
         * Best for: Large graphs, complex traversals, Cypher queries.
         */
        NEO4J("neo4j"),

        /**
         * PostgreSQL with Apache AGE extension.
         * Best for: Single-DB deployments wanting Cypher query support.
         */
        PG_AGE("pg-age"),

        /**
         * Pure PostgreSQL SQL with adjacency tables.
         * Best for: Maximum compatibility, simple traversals (1-2 hops).
         */
        PG_SQL("pg-sql"),

        /**
         * In-memory JGraphT graph store.
         * Best for: Development, testing, prototyping.
         */
        IN_MEMORY("in-memory"),

        /**
         * DuckDB SQL graph store.
         * Best for: Embedded deployments, single-file database, development.
         */
        DUCKDB("duckdb");

        private final String value;

        StoreType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Parse store type from string value.
         */
        public static StoreType fromValue(String value) {
            for (StoreType type : values()) {
                if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown graph store type: " + value + 
                ". Valid options: neo4j, pg-age, pg-sql, in-memory");
        }
    }

    /**
     * Neo4j-specific configuration.
     */
    public static class Neo4jConfig {
        private String uri = "bolt://localhost:7687";
        private String username = "neo4j";
        private String password = "password";
        private String database = "neo4j";
        private int connectionPoolSize = 50;
        private int connectionTimeout = 30000;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }

        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }

    /**
     * PostgreSQL-specific configuration (shared by pg-age and pg-sql).
     */
    public static class PostgresConfig {
        private String url = "jdbc:postgresql://localhost:5432/cef";
        private String username = "cef";
        private String password = "cefpassword";
        private String schema = "public";
        private String graphName = "cef_graph"; // For AGE
        private int connectionPoolSize = 10;
        private int maxTraversalDepth = 5; // Safety limit for recursive queries

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getGraphName() {
            return graphName;
        }

        public void setGraphName(String graphName) {
            this.graphName = graphName;
        }

        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }

        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }

        public int getMaxTraversalDepth() {
            return maxTraversalDepth;
        }

        public void setMaxTraversalDepth(int maxTraversalDepth) {
            this.maxTraversalDepth = maxTraversalDepth;
        }
    }
}
