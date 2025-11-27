# Release Notes

## Version beta-0.5 (November 27, 2025)

**First Public Beta Release**

This is the initial beta release of the Context Engineering Framework (CEF) from DDSE Foundation. CEF provides an ORM-like abstraction for LLM context engineering, managing knowledge models through dual persistence (graph + vector stores).

---

### 🎯 Release Highlights

- **ORM for Context Engineering** - Define knowledge models (nodes, edges) like JPA entities
- **Dual Persistence** - Automatic management of graph and vector stores
- **Intelligent Context Assembly** - 3-level strategy (relationship navigation → semantic → keyword)
- **Standard Patterns** - Repository layer, service patterns, lifecycle hooks
- **Comprehensive Documentation** - USER_GUIDE, ARCHITECTURE, examples

> **Note:** This is a **Community/Research Release**. It is optimized for ease of use and experimentation, not for high-concurrency enterprise production environments. It is ideal for **"Disposable Research Pods"**—ephemeral environments spun up to analyze specific datasets (e.g., financial audits, clinical trials) and then discarded.

---

### ✨ Core Features

#### Knowledge Model ORM
- **Entity Persistence** - Node and Edge entities with JSONB properties
- **Relationship Navigation** - Multi-hop graph traversal with semantic filtering
- **Vectorizable Content** - Automatic embedding generation and persistence
- **RelationType System** - Semantic hints (HIERARCHY, CAUSALITY, ASSOCIATION, etc.)

#### Storage Backends (Pluggable)
- ✅ **DuckDB** - Embedded database (default, tested)
- ✅ **JGraphT** - In-memory graph store (default, tested)
- ⚠️ **PostgreSQL** - External database with pgvector (configured, untested)
- ⚠️ **Neo4j** - Graph database for large-scale deployments (configured, untested)
- ⚠️ **Qdrant** - Vector database (configured, untested)
- ⚠️ **Pinecone** - Cloud vector database (configured, untested)

#### LLM Integration
- ✅ **vLLM** - Production inference server with Qwen3-Coder-30B-A3B-Instruct-FP8 (tested)
- ✅ **Ollama Embeddings** - nomic-embed-text model, 768 dimensions (tested)
- ⚠️ **OpenAI** - GPT-4, GPT-3.5 Turbo (configured, untested)
- ⚠️ **Ollama LLM** - Llama 3.x models (configured, untested)

#### Context Assembly
- **Pattern-Based Retrieval** - GraphPattern with TraversalStep and Constraint
- **Multi-Hop Reasoning** - Configurable depth (1-5 hops)
- **3-Level Fallback** - Graph → Hybrid → Vector-only
- **Semantic Filtering** - Relationship semantics-aware traversal

#### Developer Experience
- **Repository Pattern** - Domain-specific facades over ORM layer
- **Service Layer** - Business logic separation with transaction support
- **Reactive API** - Spring WebFlux + R2DBC for non-blocking I/O
- **Configuration** - YAML-based with sensible defaults

---

### 📦 What's Included

#### Framework (cef-framework)
```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>beta-0.5</version>
</dependency>
```

- `KnowledgeIndexer` - Entity persistence (like EntityManager)
- `KnowledgeRetriever` - Context queries (like Repository)
- `GraphStore` - Pluggable graph backend interface
- `VectorStore` - Pluggable vector backend interface
- `Node`, `Edge`, `Chunk`, `RelationType` - Core domain entities
- `GraphPattern`, `TraversalStep`, `Constraint` - Query DSL

#### Comprehensive Test Suite
- **Medical Domain:** 150 patients, 5 conditions, 7 medications, 15 doctors (177 nodes, 455 edges)
- **Financial Domain:** SAP-simulated data (vendors, materials, purchase orders, invoices)
- **Benchmarks:** 4 complex scenarios proving Knowledge Model superiority
- **Results:** 60-220% improvement over vector-only search (see [docs/EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md))

#### Documentation
- **USER_GUIDE.md** - Complete ORM integration guide (30KB, 1,200 lines)
- **ARCHITECTURE.md** - Technical deep dive
- **QUICKSTART.md** - Getting started in 5 minutes
- **KNOWN_ISSUES.md** - Testing status and limitations
- **README.md** - Project overview

---

### 🧪 Testing Status

#### Thoroughly Tested ✅
- DuckDB embedded database
- JGraphT in-memory graph (up to 100K nodes)
- vLLM with Qwen3-Coder-30B-A3B-Instruct-FP8
- Ollama embeddings (nomic-embed-text, 768 dimensions)
- Pattern-based retrieval with multi-hop reasoning
- Medical domain example with benchmarks

#### Configured but Untested ⚠️
- PostgreSQL + pgvector
- Neo4j graph database
- OpenAI GPT models
- Ollama LLM models (Llama 3.x)
- Qdrant vector database
- Pinecone vector database

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for details.

---

### 🚀 Getting Started

#### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose

#### Quick Start
```bash
# Clone repository
git clone <repository-url>
cd ced

# Start services (Ollama for embeddings)
docker-compose up -d

# Note: vLLM (Qwen3-Coder-30B) required for benchmark reproduction
# See https://docs.vllm.ai/ for installation

# Build framework
mvn clean install

# Run test suite (includes benchmarks)
cd cef-framework
mvn test

# View benchmark results
open docs/benchmark_comparison.png
open docs/EVALUATION_SUMMARY.md
```

#### Example Usage
```java
// Define knowledge model
Node patient = new Node(null, "Patient", 
    Map.of("name", "John", "age", 45), 
    "Patient John with diabetes");

// Persist entity
indexer.indexNode(patient).block();

// Define relationship
Edge hasCondition = new Edge(null, "HAS_CONDITION",
    patientId, diabetesId, null, 1.0);
indexer.indexEdge(hasCondition).block();

// Query context
SearchResult result = retriever.retrieve(
    RetrievalRequest.builder()
        .query("diabetes treatments")
        .depth(2)
        .topK(10)
        .build()
);
```

---

### 🏆 Benchmark Results

**Comprehensive test suite validates Knowledge Model superiority over vector-only approaches.**

#### Test Domains
1. **Medical Clinical Decision Support**
   - 177 nodes: Patients, Conditions, Medications, Doctors
   - 455 edges: Multi-hop relationships
   - 4 complex scenarios: Contraindication discovery, behavioral patterns, cascading risks, transitive exposure
   - **Results:** 60-220% improvement, 120% average

2. **SAP ERP Organizational Structure**
   - 80+ records: Departments, Cost Centers, Projects, Vendors, Materials, Invoices
   - 14 CSV entities with organizational hierarchies
   - 2 scenarios: Cross-project resource allocation, cost center contagion analysis
   - **Results:** 60% improvement (both scenarios), proves Graph RAG advantage for structural patterns

#### Key Findings

**Medical Domain:**

| Scenario | Vector-Only | Knowledge Model | Improvement |
|----------|-------------|-----------------|-------------|
| Multi-hop contraindication | 5 chunks | 12 chunks | **+140%** |
| Behavioral risk patterns | 5 chunks | 8 chunks | **+60%** |
| Cascading side effects | 5 chunks | 8 chunks | **+60%** |
| Transitive exposure risk | 5 chunks | 16 chunks | **+220%** 🔥 |
| **Average** | **5.0 chunks** | **11.0 chunks** | **+120%** |

**SAP ERP Domain:**

| Scenario | Vector-Only | Knowledge Model | Improvement |
|----------|-------------|-----------------|-------------|
| Cross-project resource allocation | 5 chunks | 8 chunks | **+60%** |
| Cost center contagion analysis | 5 chunks | 8 chunks | **+60%** |
| **Average** | **5.0 chunks** | **8.0 chunks** | **+60%** |

**Cross-Domain Insight:**  
Graph RAG wins for **structural organizational patterns** (Department→CostCenter hierarchies, funding networks). Graph RAG equals vector search for **semantically explicit relationships** (supply chain descriptions already mentioning vendor-component connections).

**Latency:**  
- Medical: 26ms avg (+19.5% vs vector-only 22ms)  
- SAP: 43ms avg (+23.2% vs vector-only 35ms)  
- Conclusion: <25% overhead acceptable for 60-220% content improvement

**Visualizations:**
- Medical: `cef-framework/src/test/resources/scripts/results/benchmark_comparison.png`
- SAP: `cef-framework/src/test/resources/scripts/results/sap_benchmark_comparison.png`

**See [docs/EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md) for detailed multi-domain analysis.**

---

### 📊 Performance Characteristics

**Tested Configuration:** DuckDB + JGraphT + vLLM (Qwen3-Coder-30B) + Ollama (nomic-embed-text)

| Operation | Performance | Notes |
|-----------|-------------|-------|
| Node indexing | <50ms per node | Single insert |
| Batch indexing | ~2s per 1000 nodes | Transactional batch |
| Graph traversal (depth 2) | <50ms | JGraphT in-memory |
| Vector search (10K chunks) | ~100ms | DuckDB brute-force |
| Hybrid assembly | ~150ms | Graph + vector combined |
| Embedding generation | ~200ms per chunk | Ollama nomic-embed-text |

**Benchmark Results (Medical Domain):**
- Vector-only: 60 chunks retrieved
- Knowledge Model ORM: 132 chunks retrieved (120% improvement)
- Relationship-aware context with proper entity boundaries

---

### 🔧 Configuration Example

```yaml
cef:
  # Storage backend
  database:
    type: duckdb  # or postgresql
    duckdb:
      path: ./data/cef.duckdb
  
  # Graph store
  graph:
    store: jgrapht  # or neo4j
    preload-on-startup: true
  
  # Vector store  
  vector:
    store: duckdb  # or postgres, qdrant, pinecone
  
  # LLM provider
  llm:
    default-provider: vllm  # or ollama, openai
    vllm:
      base-url: http://localhost:8001
      model: Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8
  
  # Embedding provider
  embedding:
    provider: ollama  # or openai
    model: nomic-embed-text
    dimension: 768
```

---

### 🐛 Known Limitations

1. **JGraphT Memory** - Recommended maximum 100K nodes (~350MB)
2. **PostgreSQL Untested** - Schema provided but not integration tested
3. **Concurrent Indexing** - Not thread-safe, use sequential loading
4. **DuckDB Vector Search** - Brute-force only, no HNSW index
5. **No Schema Validation** - RelationType semantics are advisory
6. **Limited Observability** - Basic metrics only, enhanced planned for v0.6

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for complete list and workarounds.

---

### 📝 Documentation

- **[USER_GUIDE.md](USER_GUIDE.md)** - Complete integration guide with ORM patterns
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Technical architecture and design decisions
- **[QUICKSTART.md](QUICKSTART.md)** - Get started in 5 minutes
- **[KNOWN_ISSUES.md](KNOWN_ISSUES.md)** - Testing status and limitations
- **[ADR-002.md](docs/ADR-002.md)** - Architecture decision record
- **[requirements.md](docs/requirements.md)** - Detailed specifications

---

### 🛣️ Roadmap

#### v0.6 (Planned - Q1 2026)
- PostgreSQL production testing and validation
- Thread-safe concurrent indexing
- Enhanced observability (metrics, tracing)
- Performance optimizations for large graphs
- OpenAI integration testing

#### v0.7 (Planned - Q2 2026)
- Intelligent context truncation
- L1/L2 caching implementation
- Multi-tenancy patterns
- Qdrant/Pinecone testing

#### v1.0 (Planned - Q3 2026)
- Production-grade release
- Comprehensive test coverage
- Auto-migration support
- Schema validation framework
- Community-tested all backends

---

### 🤝 Contributing

We welcome contributions, especially:

- Testing untested configurations (PostgreSQL, OpenAI, Neo4j)
- Performance benchmarking on different scales
- Documentation improvements
- Bug reports and fixes
- Feature requests

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for areas needing community testing and validation.

---

### 📄 License

MIT License

Copyright (c) 2024-2025 DDSE Foundation

See [LICENSE](LICENSE) file for details.

---

### 🙏 Acknowledgments

- **DDSE Foundation** - [https://ddse-foundation.github.io/](https://ddse-foundation.github.io/)
- **Author** - Mahmudur R Manna (mrmanna), Founder and Principal Architect
- Built with Spring Boot, Spring AI, JGraphT, DuckDB, and pgvector
- Inspired by Hibernate/JPA ORM patterns

---

### 📞 Support

- **Documentation:** [User Guide](USER_GUIDE.md) | [Architecture](docs/ARCHITECTURE.md)
- **Issues:** GitHub Issues (repository link TBD)
- **Community:** DDSE Foundation website
- **Email:** Contact through DDSE Foundation

---

**Thank you for trying CEF beta-0.5!**

We appreciate your feedback as we work toward v1.0. Please report any issues or share your success stories with the community.

---

**DDSE Foundation** - Decision-Driven Software Engineering  
[https://ddse-foundation.github.io/](https://ddse-foundation.github.io/)
