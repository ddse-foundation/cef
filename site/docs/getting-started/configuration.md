---
sidebar_position: 2
---

# Configuration

Comprehensive configuration guide for CEF Framework.

## Configuration Overview

CEF uses Spring Boot's configuration system with YAML files. All configuration is under the `cef` namespace.

```yaml
cef:
  database: # Database backend configuration
  graph: # Graph store configuration
  vector: # Vector store configuration
  llm: # LLM provider configuration
  embedding: # Embedding configuration
  retrieval: # Retrieval strategy configuration
  indexing: # Indexing configuration
```

## Database Configuration

### DuckDB (Default)

Embedded database, perfect for development and testing:

```yaml
cef:
  database:
    type: duckdb
    duckdb:
      path: ./data/cef.duckdb  # Database file location
      schema: graph  # Schema name
      in-memory: false  # Set true for in-memory database
```

**Pros:**
- Zero configuration
- Fast for &lt;100K entities
- Embedded, no external services
- Great for development/testing

**Cons:**
- Single-threaded writes
- Limited to one process
- No true ACID transactions

### PostgreSQL

Production-grade database with pgvector extension:

```yaml
cef:
  database:
    type: postgresql
    postgresql:
      enabled: true
      host: localhost
      port: 5432
      database: cef_db
      username: cef_user
      password: ${DB_PASSWORD}  # Use environment variable
      schema: graph
      pool-size: 20  # Connection pool size
```

**Spring R2DBC Connection** (required for reactive database access):

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: ${DB_PASSWORD}
    pool:
      initial-size: 5
      max-size: 20
      max-idle-time: 30m
```

**Pros:**
- Production-grade ACID compliance
- Concurrent read/write
- pgvector extension for efficient vector search
- Battle-tested scalability

**Cons:**
- Requires external service
- More complex setup

## Graph Store Configuration

### JGraphT (Default)

In-memory graph with O(1) lookups:

```yaml
cef:
  graph:
    store: jgrapht
    in-memory: true
    load-on-startup: true  # Preload graph from database
    max-traversal-depth: 5  # Maximum depth for graph traversal
```

**Recommended for:** &lt;100K nodes, development, fast reads

### Neo4j (Planned)

Dedicated graph database for large-scale deployments:

```yaml
cef:
  graph:
    store: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: ${NEO4J_PASSWORD}
      database: cef
```

**Recommended for:** >100K nodes, production, complex graph queries

## Vector Store Configuration

### DuckDB Vector Store

Uses DuckDB's vector functions:

```yaml
cef:
  vector:
    store: duckdb
    dimension: 768  # Embedding dimension (nomic-embed-text default)
    distance-metric: cosine  # cosine, l2, inner_product
```

**Pros:**
- Same database as graph data
- Simple setup
- Fast for &lt;10K chunks

**Cons:**
- Brute-force search only (no HNSW index)
- Slower for >10K chunks

### PostgreSQL Vector Store

Uses pgvector extension with HNSW index:

```yaml
cef:
  vector:
    store: postgres
    dimension: 768
    distance-metric: cosine
    postgres:
      hnsw-index: true  # Enable HNSW index
      hnsw-m: 16  # HNSW index parameter (higher = more accurate, slower build)
      hnsw-ef-construction: 64  # HNSW build parameter
```

**Pros:**
- HNSW index for fast approximate search
- Scalable to millions of vectors
- Production-grade

**Cons:**
- Requires pgvector extension
- More complex setup

### Qdrant (Planned)

Specialized vector database:

```yaml
cef:
  vector:
    store: qdrant
    qdrant:
      host: localhost
      port: 6333
      collection: cef_vectors
      dimension: 768
```

## LLM Provider Configuration

### Ollama (Recommended for Development)

Local LLM server:

```yaml
cef:
  llm:
    default-provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3.2:3b  # or llama3.1:70b, qwen2.5:32b
      timeout: 60s
```

### vLLM (Recommended for Production)

High-performance inference server:

```yaml
cef:
  llm:
    default-provider: vllm
    vllm:
      base-url: http://localhost:8000
      model: Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
      max-tokens: 4096
      temperature: 0.7
```

### OpenAI

Cloud-hosted LLM:

```yaml
cef:
  llm:
    default-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      base-url: https://api.openai.com
      timeout: 30s
```

## Embedding Configuration

### Ollama Embeddings (Default)

```yaml
cef:
  embedding:
    provider: ollama
    model: nomic-embed-text
    dimension: 768
    batch-size: 100  # Batch size for embedding generation
```

**Models available:**
- `nomic-embed-text` (768 dims) - General purpose, default
- `mxbai-embed-large` (1024 dims) - Higher quality
- `all-minilm` (384 dims) - Smaller, faster

### OpenAI Embeddings

```yaml
cef:
  embedding:
    provider: openai
    model: text-embedding-3-small
    dimension: 1536
    api-key: ${OPENAI_API_KEY}
```

**Models available:**
- `text-embedding-3-small` (1536 dims) - Cost-effective
- `text-embedding-3-large` (3072 dims) - Highest quality
- `text-embedding-ada-002` (1536 dims) - Legacy model

## Retrieval Configuration

### Hybrid Retrieval Strategy

```yaml
cef:
  retrieval:
    default-strategy: hybrid  # hybrid, vector, graph
    hybrid:
      vector-weight: 0.7  # Weight for semantic similarity
      bm25-weight: 0.3  # Weight for keyword matching
    top-k: 10  # Number of chunks to retrieve
    min-score: 0.5  # Minimum similarity score
    fallback-threshold: 3  # Fall back to vector-only if <3 graph results
```

### Strategy Options

1. **hybrid** (default): Combines graph traversal + semantic search
2. **vector**: Pure semantic search only
3. **graph**: Graph traversal only

## Indexing Configuration

```yaml
cef:
  indexing:
    batch-size: 100  # Batch size for bulk indexing
    chunk-size: 512  # Tokens per chunk
    chunk-overlap: 50  # Overlapping tokens between chunks
    auto-embed: true  # Automatically generate embeddings on index
    parallel: false  # Parallel indexing (experimental)
```

## Context Assembly Configuration

```yaml
cef:
  context:
    token-budget: 4000  # Maximum tokens for assembled context
    max-queries: 5  # Maximum graph queries per retrieval
    deduplicate: true  # Remove duplicate chunks
    include-metadata: true  # Include chunk metadata
```

## Complete Example Configuration

### Development Setup

```yaml
cef:
  database:
    type: duckdb
    duckdb:
      path: ./data/cef.duckdb
  
  graph:
    store: jgrapht
    in-memory: true
    load-on-startup: true
  
  vector:
    store: duckdb
    dimension: 768
  
  llm:
    default-provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3.2:3b
  
  embedding:
    provider: ollama
    model: nomic-embed-text
    dimension: 768
  
  retrieval:
    default-strategy: hybrid
    top-k: 10

logging:
  level:
    org.ddse.ml.cef: DEBUG
```

### Production Setup

```yaml
cef:
  database:
    type: postgresql
    postgresql:
      enabled: true
      host: ${DB_HOST}
      port: 5432
      database: cef_production
      username: ${DB_USER}
      password: ${DB_PASSWORD}
      pool-size: 50
  
  graph:
    store: jgrapht  # or neo4j for >100K nodes
    in-memory: true
    load-on-startup: true
  
  vector:
    store: postgres
    dimension: 768
    postgres:
      hnsw-index: true
      hnsw-m: 16
  
  llm:
    default-provider: vllm
    vllm:
      base-url: ${VLLM_URL}
      model: Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
  
  embedding:
    provider: ollama
    model: nomic-embed-text
    dimension: 768
    batch-size: 200
  
  retrieval:
    default-strategy: hybrid
    top-k: 20
    min-score: 0.6

spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:5432/cef_production
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    pool:
      initial-size: 10
      max-size: 50

logging:
  level:
    org.ddse.ml.cef: INFO
    org.springframework.ai: WARN
```

## Environment Variables

Use environment variables for sensitive configuration:

```bash
# .env file
DB_PASSWORD=your_secure_password
OPENAI_API_KEY=sk-...
VLLM_URL=http://vllm-server:8000
```

Access in configuration:

```yaml
cef:
  database:
    postgresql:
      password: ${DB_PASSWORD}
```

## Configuration Profiles

Use Spring profiles for environment-specific configuration:

```yaml
# application.yml (shared)
cef:
  embedding:
    model: nomic-embed-text

---
# application-dev.yml
spring:
  config:
    activate:
      on-profile: dev

cef:
  database:
    type: duckdb

---
# application-prod.yml
spring:
  config:
    activate:
      on-profile: prod

cef:
  database:
    type: postgresql
```

Run with profile:

```bash
java -jar app.jar --spring.profiles.active=prod
```

## Next Steps

- Learn about [Knowledge Models](../concepts/knowledge-model)
- Follow the [Quick Start Tutorial](../quickstart)
- Explore [Advanced Configuration](../advanced/custom-backends)
