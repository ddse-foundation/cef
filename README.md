# CEF - Context Engineering Framework

**Research-Grade ORM for LLM Context Engineering - Persist Knowledge Models, Query Context Intelligently**

> **⚠️ Community Edition Notice:** This framework is designed for **Developers (Rapid Prototyping)** and **Academics (Experimentation)**. It is **NOT** currently engineered for Enterprise Production use (see [KNOWN_ISSUES.md](KNOWN_ISSUES.md)).

[![Version](https://img.shields.io/badge/version-beta--0.5-blue.svg)](RELEASE_NOTES.md)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Overview

**CEF is an ORM for LLM context engineering** - just as Hibernate abstracts relational databases for transactional data, CEF abstracts knowledge stores for LLM context. 

**✅ Validated with comprehensive benchmarks:** Knowledge Model retrieves **60-220% more relevant content** than vector-only approaches for complex queries requiring relationship reasoning.

### Target Audience

- 👩‍💻 **Developers**: Rapidly prototype LLM applications with rich context without setting up complex infrastructure.
- 🎓 **Academics**: Experiment with GraphRAG algorithms and benchmark against vector-only baselines.
- 🧪 **Researchers**: Reproducible environment for testing context engineering strategies.
- 🏢 **Enterprise Research Pods**: Deploy ephemeral, self-contained analysis environments for specific datasets (e.g., "Annual GL Analysis") without requiring permanent heavy infrastructure.

### Core Capabilities

- 🗄️ **Knowledge Model ORM** - Define entities (nodes) and relationships (edges) like JPA @Entity
- 🔄 **Dual Persistence** - Graph store (relationships) + Vector store (semantics)
- 🔍 **Intelligent Context Assembly** - Relationship navigation + semantic search + keyword fallback
- 📦 **Storage Agnostic** - Pluggable backends (JGraphT, Neo4j, Postgres, Qdrant)
- 🔌 **LLM Integration** - OpenAI, Ollama, vLLM with MCP tool support
- 📄 **Parser System** - PDF, YAML, CSV, JSON with ANTLR support
- ☁️ **Storage Adapters** - FileSystem, S3/MinIO
- ⚡ **Fully Reactive** - Spring WebFlux + R2DBC

**Author:** Mahmudur R Manna (mrmanna) - Founder and Principal Architect of DDSE  
**Organization:** [DDSE Foundation](https://ddse-foundation.github.io/) (Decision-Driven Software Engineering)  
**Date:** 2024

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│          (Define Knowledge Models: Entities & Relations)     │
└─────────────────────────────────────────────────────────────┘
                             │
                 ┌───────────┴───────────┐
                 │    ORM Interface       │
                 │  1. KnowledgeIndexer   │  (like EntityManager)
                 │  2. KnowledgeRetriever │  (like Repository)
                 └────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                  CEF ORM Engine                              │
│  • Knowledge Model Manager                                   │
│  • Relationship Navigator (Graph reasoning)                  │
│  • Context Assembler (Multi-strategy)                        │
│  • Parser System (Domain transformation)                     │
│  • DataSource Adapters (FileSystem, S3/MinIO)               │
│  • Dual Persistence Coordinator                              │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                   Storage Layer                              │
│  Graph Store: Node, Edge, RelationType (relationships)       │
│  Vector Store: Chunk with embeddings (semantic context)      │
│  Backends: DuckDB, PostgreSQL, Neo4j, Qdrant                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Clone and Build

```bash
git clone <repository-url>
cd cef
mvn clean install
```

### 2. Start Infrastructure

```bash
# Default: Only Ollama (DuckDB embedded, no external DB needed)
docker-compose up -d

# With PostgreSQL (optional - demonstrates agnosticism)
docker-compose --profile postgres up -d

# With MinIO (optional - demonstrates blob storage)
docker-compose --profile minio up -d

# All services
docker-compose --profile postgres --profile minio up -d
```

### 3. Run Framework Tests

```bash
# Run comprehensive test suite with benchmarks
cd cef-framework
mvn test

# View benchmark results
cat target/surefire-reports/org.ddse.ml.cef.benchmark.MedicalBenchmarkTest.txt
```

### 4. Access Services

- **Ollama**: http://localhost:11434/api/tags
- **MinIO Console** (if enabled): http://localhost:9001
- **PostgreSQL** (if enabled): localhost:5432

---

## Project Structure

```
ced/
├── cef-framework/          # Core framework (JAR library)
│   ├── src/main/java/      # ORM implementation
│   │   └── org/ddse/ml/cef/
│   │       ├── domain/     # Node, Edge, Chunk, RelationType
│   │       ├── api/        # KnowledgeIndexer, KnowledgeRetriever
│   │       ├── storage/    # GraphStore, VectorStore interfaces
│   │       ├── retriever/  # Pattern-based retrieval
│   │       └── graph/      # JGraphT integration
│   ├── src/test/java/      # Comprehensive test suite
│   │   └── org/ddse/ml/cef/
│   │       ├── benchmark/  # Performance benchmarks
│   │       ├── integration/# Medical domain tests
│   │       └── base/       # SAP financial data tests
│   └── pom.xml
│
├── docs/
│   ├── EVALUATION_SUMMARY.md   # Benchmark analysis
│   ├── benchmark_comparison.png # Performance charts
│   ├── ARCHITECTURE.md         # Technical architecture
│   └── requirements.md         # Specifications
│
├── USER_GUIDE.md           # ORM integration guide
├── RELEASE_NOTES.md        # Version beta-0.5
├── KNOWN_ISSUES.md         # Testing status
├── docker-compose.yml      # vLLM + Ollama services
└── pom.xml                 # Parent POM
```

---

## Configuration

### Default (DuckDB + Ollama)

```yaml
cef:
  database:
    type: duckdb
    duckdb:
      path: ./data/cef.duckdb
  
  llm:
    default-provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3.2:3b
```

**Note:** Benchmark tests use vLLM (Qwen3-Coder-30B) which requires separate installation. See [vLLM documentation](https://docs.vllm.ai/) for setup.

### Optional (PostgreSQL)

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
      password: cef_password
```

### Optional (MinIO/S3)

```yaml
cef:
  datasources:
    blob-storage:
      enabled: true
      endpoint: http://localhost:9000
      bucket: medical-documents
      access-key: minioadmin
      secret-key: minioadmin
```

---

## Usage

### 1. Framework Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>beta-0.5</version>
</dependency>
```

**Note:** Beta release tested with DuckDB, vLLM (Qwen3-Coder-30B for generation), and Ollama (nomic-embed-text for embeddings). OpenAI integration is configured but untested. See [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

### 2. Define Domain Entities

```java
// Your domain - framework doesn't know these
public record PatientDTO(UUID id, String name, int age, String condition) {}
```

### 3. Create Custom Parser

```java
@Component
public class MedicalPdfParser extends AbstractParser<MedicalParsedData> {
    // Parse PDFs into Node/Edge/Chunk inputs
}
```

### 4. Persist Knowledge Models

```java
@Autowired
private KnowledgeIndexer indexer;  // Like EntityManager

// Initialize ORM with relation types (like JPA entity mappings)
indexer.initialize(rootNodes, relationTypes);

// Bulk persist from data source (like StatelessSession)
IndexResult result = indexer.fullIndex(dataSource);
```

### 5. Query Context

```java
@Autowired
private KnowledgeRetriever retriever;  // Like Repository

// Intelligent context assembly via relationship navigation
SearchResult result = retriever.retrieve(
    RetrievalRequest.builder()
        .query("Show patients with diabetes")
        .depth(2)  // Navigation depth through relationships
        .topK(10)
        .build()
);
```

---

## Benchmark Results: Knowledge Model Superiority

Comprehensive test suite with **real-world scenarios** proves Knowledge Model (graph + vector) significantly outperforms vector-only approaches:

### Medical Domain Tests
- **177 nodes:** 150 patients, 5 conditions, 7 medications, 15 doctors
- **455 edges:** Patient-Condition, Patient-Medication, Patient-Doctor relationships
- **177 vectorized chunks:** Clinical notes, condition profiles, medication profiles

### Financial Domain Tests (SAP-Simulated)
- **Enterprise data:** Vendors, materials, purchase orders, invoices
- **Complex relationships:** Procurement workflows, financial transactions

### Performance Comparison

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 avg | 9.75 avg | **+95%** |
| Latency | 21.8ms | 26.0ms | +19.5% |
| Multi-hop Queries | Limited | **Full graph traversal** | ✅ |
| Structural Coverage | Semantic only | **Entity relationships** | ✅ |

**Key Finding:** Knowledge Model retrieves **60-220% more relevant content** for complex queries requiring relationship reasoning.

![Benchmark Results](docs/benchmark_comparison.png)

See [EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md) for detailed analysis.

---

## Documentation

- [USER_GUIDE.md](USER_GUIDE.md) - Complete ORM integration guide
- [EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md) - Benchmark analysis (60-220% improvement proven)
- [RELEASE_NOTES.md](RELEASE_NOTES.md) - Version beta-0.5 release notes
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - Testing status and limitations
- [QUICKSTART.md](QUICKSTART.md) - Get started in 5 minutes
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) - Technical architecture
- [requirements.md](docs/requirements.md) - Detailed specifications

---

## Technology Stack

- **Java 17** - Language
- **Spring Boot 3.3.5** - Application framework
- **Spring AI 1.0.0-M4** - LLM integration
- **Spring WebFlux** - Reactive web
- **Spring Data R2DBC** - Reactive database
- **JGraphT 1.5.2** - In-memory graph
- **ANTLR 4.13.1** - Parser generator
- **DuckDB 1.1.3** - Default embedded database
- **PostgreSQL 16** - Optional external database (with pgvector)
- **Apache PDFBox 3.0.3** - PDF processing

---

## License

MIT License

Copyright (c) 2024-2025 DDSE Foundation

See [LICENSE](LICENSE) file for full license text.

---

## Contributing

Contributions are welcome! Please:

1. Test untested configurations (PostgreSQL, OpenAI, Neo4j)
2. Report issues with detailed logs and reproduction steps
3. Submit pull requests with test coverage
4. Review [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for areas needing validation

For questions, contact DDSE Foundation at https://ddse-foundation.github.io/

---

## Authors

- **Mahmudur R Manna (mrmanna)** - Founder and Principal Architect, [DDSE Foundation](https://ddse-foundation.github.io/)

---

## About DDSE Foundation

This framework is developed by the **DDSE Foundation** (Decision-Driven Software Engineering), an open-source initiative advancing principled approaches to software architecture and engineering.

