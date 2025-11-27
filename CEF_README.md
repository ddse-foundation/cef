# Context Engineering Framework (CEF)

**Production-ready ORM for LLM Context Engineering - Hibernate for Knowledge Models**

[![Java](https://img.shields.io/badge/Java-17-blue)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()

## Overview

CEF is a **production-grade ORM for LLM context engineering** - just as Hibernate abstracts relational databases for transactional data, CEF abstracts knowledge stores for LLM context. Unlike domain-specific solutions, CEF is completely framework-agnostic - you define your knowledge models (entities, relationships), and CEF handles persistence, caching, and intelligent context assembly.

### Key Features

✅ **ORM for Knowledge Models**: Define entities (Node) and relationships (Edge) like JPA @Entity  
✅ **Dual Persistence**: Graph store (relationships) + Vector store (semantics)  
✅ **Intelligent Context Assembly**: 3-level strategy (relationship navigation → semantic → keyword)  
✅ **Pluggable Storage**: Swap graph stores (JGraphT/Neo4j) and vector stores (Postgres/Qdrant/Pinecone)  
✅ **MCP Tool Integration**: Dynamic schema injection for LLM tool calling  
✅ **Reactive & Scalable**: Built on R2DBC for non-blocking I/O  
✅ **Spring Boot Native**: Auto-configuration, dependency injection, standard patterns  
✅ **Beta Release**: Tested with vLLM, Ollama, DuckDB, JGraphT (see KNOWN_ISSUES.md)

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
    <version>beta-0.5</version>
</dependency>
```

> **Beta Release:** Tested with DuckDB, vLLM (Qwen3-Coder-30B), and Ollama (nomic-embed-text 768d). See [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

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

### 4. Persist Knowledge Models

```java
// Persist entity (like EntityManager.persist)
Node doctor = new Node(null, "Doctor", 
    Map.of("name", "Dr. Smith"), 
    "Dr. Smith is a cardiologist");
indexer.indexNode(doctor).block();

// Persist relationship (like cascading @OneToMany)
Edge treats = new Edge(null, "TREATS", 
    doctorId, patientId, null, 1.0);
indexer.indexEdge(treats).block();

// Persist vectorizable content (dual persistence)
Chunk chunk = new Chunk(null, 
    "Patient has chest pain...", 
    null, patientId, Map.of("source", "notes"));
indexer.indexChunk(chunk).block();
```

### 5. Query Context

```java
// Via MCP Tool (for LLM integration)
RetrievalRequest request = new RetrievalRequest(
    "What treatment is recommended for heart disease?"
);
String context = mcpTool.invoke(request).block();

// Or directly
RetrievalResult result = retriever.retrieve(request).block();
```

## Context Assembly Strategies

CEF automatically selects the optimal strategy (like query optimizer in RDBMS):

### 1. **Relationship Navigation** (when entity hints provided)
```java
GraphHints hints = new GraphHints();
hints.setStartNodeLabels(List.of("Doctor"));
hints.setRelationTypes(List.of("TREATS"));
request.setGraphHints(hints);
```
Traverses relationships (like JOIN operations) to gather related entities.

### 2. **Hybrid Assembly** (default)
- Semantic search for relevant content
- Navigate to linked entities
- Extract relationship subgraph
- Assemble combined context

### 3. **Semantic Fallback**
- Pure semantic search when no relationship hints available

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

## Proven in Production-Scale Tests

**Medical Domain:** 177 nodes (patients, conditions, medications, doctors), 455 relationships  
**Financial Domain:** SAP-simulated enterprise data (vendors, materials, invoices)

**Benchmark Results:** Knowledge Model retrieves 60-220% more relevant content than vector-only approaches.

```bash
# Run comprehensive test suite
cd cef-framework
mvn test

# View results
cat target/surefire-reports/org.ddse.ml.cef.benchmark.MedicalBenchmarkTest.txt
```

See [docs/EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md) for detailed analysis.

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

## Comparison: ORM Paradigm

| Feature | CEF (Knowledge ORM) | Hibernate (Data ORM) | LangChain/LlamaIndex |
|---------|---------------------|----------------------|----------------------|
| Language | Java/Spring | Java/Spring | Python |
| Domain Model | Knowledge entities | Data entities | Document-centric |
| Persistence | Dual (Graph + Vector) | Single (RDBMS) | Vector only |
| Relationships | First-class | First-class | Limited |
| Caching | L1/L2 support | L1/L2 support | Manual |
| Querying | Relationship navigation | JPQL/Criteria | Similarity search |
| Lifecycle Hooks | @PrePersist, etc. | @PrePersist, etc. | None |
| Transaction Support | Reactive | Standard | None |
| Schema Evolution | Migrations | Migrations | Manual |
| Production Ready | ✅ | ✅ | ⚠️ |

## Documentation

- [User Guide](USER_GUIDE.md) - Complete ORM integration guide
- [Release Notes](RELEASE_NOTES.md) - Version beta-0.5 details
- [Known Issues](KNOWN_ISSUES.md) - Testing status and limitations
- [Quick Start](QUICKSTART.md) - Get started in 5 minutes
- [Architecture Deep Dive](docs/ARCHITECTURE.md)
- [Technical Design](docs/ADR-002.md)
- [Requirements](docs/requirements.md)

## Contributing

Contributions welcome! We especially need:

- Testing untested configurations (PostgreSQL, OpenAI, Neo4j, Qdrant)
- Performance benchmarking at scale
- Documentation improvements
- Bug reports with reproduction steps

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for areas needing validation.

## License

MIT License

Copyright (c) 2024-2025 DDSE Foundation

See [LICENSE](LICENSE) file for full license text.

## Authors

- **Mahmudur R Manna (mrmanna)** - Founder and Principal Architect, [DDSE Foundation](https://ddse-foundation.github.io/)

## About DDSE Foundation

Developed by the **DDSE Foundation** (Decision-Driven Software Engineering), this framework represents our commitment to principled software architecture and open-source innovation.

## Acknowledgments

- Built with Spring Boot, Spring AI, JGraphT, pgvector
- Inspired by production ORM patterns from Hibernate/JPA
- Community-driven open source from DDSE Foundation
