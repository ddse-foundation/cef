# Context Engineering Framework (CEF)

**Production-ready Spring Boot framework for domain-agnostic context engineering with hybrid graph + vector retrieval.**

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-17-blue)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()

## Overview

CEF is a production-grade Java framework that combines **knowledge graphs** and **vector search** to provide LLMs with rich, structured context. Unlike domain-specific solutions, CEF is completely framework-agnostic - you define your domain model, and CEF handles the rest.

### Key Features

✅ **Hybrid Retrieval**: 3-level fallback strategy (graph-only → hybrid → vector-only)  
✅ **Pluggable Architecture**: Swap graph stores (JGraphT/Neo4j) and vector stores (Postgres/Qdrant/Pinecone)  
✅ **MCP Tool Integration**: Dynamic schema injection for LLM tool calling  
✅ **Reactive & Scalable**: Built on R2DBC for non-blocking I/O  
✅ **Spring Boot Native**: Auto-configuration, dependency injection, standard patterns  
✅ **Production Ready**: Batch indexing, error resilience, monitoring

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      LLM (GPT-4, Claude)                    │
│                  calls MCP Tool with query                  │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
          ┌───────────────────────┐
          │   McpContextTool      │
          │  (Dynamic Schema)     │
          └───────────┬───────────┘
                      │
                      ▼
          ┌───────────────────────┐
          │  KnowledgeRetriever   │
          │  (3-Level Fallback)   │
          └───────┬───────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌──────────────┐   ┌──────────────┐
│  GraphStore  │   │ VectorStore  │
│  (JGraphT)   │   │  (pgvector)  │
└──────────────┘   └──────────────┘
        │                   │
        ▼                   ▼
┌──────────────┐   ┌──────────────┐
│  Postgres    │   │  Postgres    │
│  (Entities)  │   │  (Vectors)   │
└──────────────┘   └──────────────┘
```

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure `application.yml`

```yaml
cef:
  graph:
    store: jgrapht  # or neo4j
  vector:
    store: postgres  # or duckdb, qdrant, pinecone
    dimension: 1536
  mcp:
    required-fields:
      - textQuery

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small

  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: postgres
    password: postgres
```

### 3. Define Your Domain

```java
// Register domain-specific relation types
List<RelationType> types = List.of(
    new RelationType("TREATS", "Doctor", "Patient", CAUSAL, true),
    new RelationType("PRESCRIBES", "Doctor", "Medication", CAUSAL, true)
);
indexer.initialize(types).block();
```

### 4. Index Data

```java
// Index nodes
Node doctor = new Node(null, "Doctor", 
    Map.of("name", "Dr. Smith"), 
    "Dr. Smith is a cardiologist");
indexer.indexNode(doctor).block();

// Index edges
Edge treats = new Edge(null, "TREATS", 
    doctorId, patientId, null, 1.0);
indexer.indexEdge(treats).block();

// Index text chunks (auto-embedding)
Chunk chunk = new Chunk(null, 
    "Patient has chest pain...", 
    null, patientId, Map.of("source", "notes"));
indexer.indexChunk(chunk).block();
```

### 5. Retrieve Context

```java
// Via MCP Tool (for LLM integration)
RetrievalRequest request = new RetrievalRequest(
    "What treatment is recommended for heart disease?"
);
String context = mcpTool.invoke(request).block();

// Or directly
RetrievalResult result = retriever.retrieve(request).block();
```

## Retrieval Strategies

CEF automatically selects the optimal strategy:

### 1. **Graph-Only** (when graphHints provided)
```java
GraphHints hints = new GraphHints();
hints.setStartNodeLabels(List.of("Doctor"));
hints.setRelationTypes(List.of("TREATS"));
request.setGraphHints(hints);
```

### 2. **Hybrid** (default)
- Semantic search for top-k chunks
- Get linked nodes from chunks
- Extract k-hop subgraph around nodes
- Return combined context

### 3. **Vector-Only** (fallback)
- Pure semantic search when graph is empty

## Production Features

### Pluggable Storage

#### Graph Stores
- **JGraphT** (default): In-memory, <100K nodes, O(1) lookups
- **Neo4j**: Millions of nodes, Cypher queries
- **TinkerPop**: Gremlin support

#### Vector Stores
- **PostgreSQL + pgvector** (default): Production-ready, HNSW index
- **DuckDB**: Embedded analytics
- **Qdrant**: Dedicated vector database
- **Pinecone**: Managed cloud service

### Batch Processing

```java
// Efficient bulk loading
indexer.indexNodes(largeNodeList).collectList().block();
indexer.indexEdges(largeEdgeList).collectList().block();
indexer.indexChunks(largeChunkList).collectList().block();
```

### Monitoring

```java
IndexStats stats = indexer.getStatistics().block();
// totalNodes, totalEdges, totalChunks, chunksWithEmbeddings

GraphStats graphStats = graphStore.getStatistics().block();
// nodeCount, edgeCount, averageDegree, nodeCountByLabel

VectorStats vectorStats = vectorStore.getStatistics().block();
// totalChunks, chunksWithEmbeddings, embeddingDimension
```

## MCP Tool Schema

The framework provides a dynamic MCP tool schema that adapts to configuration:

```json
{
  "name": "retrieve_context",
  "description": "Retrieve context from knowledge graph and vector store",
  "parameters": {
    "type": "object",
    "properties": {
      "textQuery": {
        "type": "string",
        "description": "Natural language query"
      },
      "graphHints": {
        "type": "object",
        "description": "Optional graph traversal hints"
      },
      "topK": {
        "type": "integer",
        "default": 10
      },
      "graphDepth": {
        "type": "integer",
        "default": 2
      }
    },
    "required": ["textQuery"]
  }
}
```

## Example: Medical Domain

See `cef-example` module for complete working example:

```bash
cd cef-example
mvn spring-boot:run
```

## Performance Characteristics

| Operation | JGraphT | Neo4j | pgvector |
|-----------|---------|-------|----------|
| Node lookup | O(1) | O(log n) | - |
| K-hop traversal | O(k×n) | O(k) | - |
| Vector search | - | - | O(log n) HNSW |
| Bulk insert | O(n) | O(n log n) | O(n) |
| Memory | ~100KB/10K nodes | Disk-based | Disk-based |

## Database Schema

### Nodes
```sql
CREATE TABLE nodes (
    id UUID PRIMARY KEY,
    label VARCHAR(255),
    properties JSONB,
    vectorizable_content TEXT,
    created TIMESTAMP,
    updated TIMESTAMP,
    version BIGINT
);
```

### Edges
```sql
CREATE TABLE edges (
    id UUID PRIMARY KEY,
    relation_type VARCHAR(255),
    source_node_id UUID REFERENCES nodes(id),
    target_node_id UUID REFERENCES nodes(id),
    properties JSONB,
    weight DOUBLE PRECISION,
    created TIMESTAMP
);
```

### Chunks
```sql
CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    content TEXT,
    embedding vector(1536),  -- pgvector extension
    linked_node_id UUID REFERENCES nodes(id),
    metadata JSONB,
    created TIMESTAMP
);

CREATE INDEX ON chunks USING hnsw (embedding vector_cosine_ops);
```

## Comparison vs Alternatives

| Feature | CEF | LangChain | LlamaIndex | Neo4j+RAG |
|---------|-----|-----------|------------|-----------|
| Language | Java/Spring | Python | Python | Any |
| Graph + Vector | ✅ Hybrid | ❌ Separate | ❌ Separate | ✅ Plugin |
| Domain-agnostic | ✅ | ❌ | ❌ | ✅ |
| Production-ready | ✅ | ⚠️ | ⚠️ | ✅ |
| Pluggable storage | ✅ | ❌ | ❌ | ⚠️ |
| MCP native | ✅ | ❌ | ❌ | ❌ |
| Spring Boot | ✅ | ❌ | ❌ | ❌ |

## Documentation

- [Architecture Deep Dive](docs/ARCHITECTURE.md)
- [ADR-002: Technical Design](docs/ADR-002.md)
- [Requirements](docs/requirements.md)
- [Implementation Plan](docs/IDR-002.md)

## Contributing

Contributions welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT License - see [LICENSE](LICENSE)

## Authors

- **mrmanna** - *Initial work*

## Acknowledgments

- Built with Spring Boot, Spring AI, JGraphT, pgvector
- Inspired by Neo4j GraphRAG, LangChain, and LlamaIndex
- Presented at AI Conference 2025
