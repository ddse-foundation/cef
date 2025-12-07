# CEF - Context Engineering Framework

**Research-Grade ORM for LLM Context Engineering - Persist Knowledge Models, Query Context Intelligently**

> **Research Edition (v0.6):** Designed for **Developers (rapid prototyping)** and **Academics/Researchers**. Production patterns implemented (resilience, security, validation) but not hardened for enterprise deployment. See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for gaps.

[![Version](https://img.shields.io/badge/version-v0.6--research-blue.svg)](RELEASE_NOTES.md)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/tests-178%2B%20passing-brightgreen.svg)](cef-framework/src/test)

---

## Overview

**CEF is an ORM for LLM context engineering** - just as Hibernate abstracts relational databases for transactional data, CEF abstracts knowledge stores for LLM context. 

**✅ Validated with comprehensive benchmarks:** Knowledge Model retrieves **60-220% more relevant content** than vector-only approaches for complex queries requiring relationship reasoning.

### What's New in v0.6

- 🗃️ **Pluggable Graph Stores** - Neo4j, PostgreSQL (AGE + pure SQL), DuckDB, In-Memory (config-driven)
- 🛡️ **Security Foundations** - API-key/basic auth, input sanitization, audit logging (opt-in)
- 🔄 **Resilience Patterns** - Retry, circuit breaker, timeout for embedding services
- ✅ **178+ Integration Tests** - Real infrastructure via Testcontainers (no mocks)
- 🐳 **Docker Compose** - Neo4j, PostgreSQL+pgvector, Apache AGE, MinIO

### Target Audience

- 👩‍💻 **Developers**: Rapidly prototype LLM applications with rich context without setting up complex infrastructure.
- 🎓 **Academics**: Experiment with GraphRAG algorithms and benchmark against vector-only baselines.
- 🧪 **Researchers**: Reproducible environment for testing context engineering strategies.
- 🏢 **Enterprise Research Pods**: Deploy ephemeral, self-contained analysis environments for specific datasets (e.g., "Annual GL Analysis") without requiring permanent heavy infrastructure.

### Core Capabilities

- 🗄️ **Knowledge Model ORM** - Define entities (nodes) and relationships (edges) like JPA @Entity
- 🔄 **Dual Persistence** - Graph store (relationships) + Vector store (semantics)
- 🔍 **Intelligent Context Assembly** - Relationship navigation + semantic search + keyword fallback
- 📦 **Storage Agnostic** - Pluggable backends: **Neo4j**, **PostgreSQL (AGE/SQL)**, **DuckDB**, **JGraphT**, **pgvector**
- 🔌 **LLM Integration** - OpenAI, Ollama, vLLM with MCP tool support
- 📄 **Parser System** - PDF, YAML, CSV, JSON with ANTLR support
- ☁️ **Storage Adapters** - FileSystem, S3/MinIO
- ⚡ **Fully Reactive** - Spring WebFlux + R2DBC

**Author:** Mahmudur R Manna (mrmanna) - Founder and Principal Architect of DDSE  
**Organization:** [DDSE Foundation](https://ddse-foundation.github.io/) (Decision-Driven Software Engineering)  
**Date:** 2024-2025

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
│  • Resilience Layer (Retry, Circuit Breaker, Timeout)       │
│  • Security Layer (Auth, Sanitization, Audit)               │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│                   Storage Layer (v0.6)                       │
│  Graph Store: Neo4j │ PostgreSQL+AGE │ PostgreSQL SQL │     │
│                DuckDB │ In-Memory (JGraphT)                  │
│  Vector Store: PostgreSQL+pgvector │ DuckDB VSS │ In-Memory │
│  Selection: Single property (cef.graph.store, cef.vector.store) │
└─────────────────────────────────────────────────────────────┘
```

### Graph Store Options (v0.6)

| Store | Backend | Best For | Config Value |
|-------|---------|----------|--------------|
| **Neo4jGraphStore** | Neo4j 5.x | Large graphs, complex Cypher | `neo4j` |
| **PgAgeGraphStore** | PostgreSQL + Apache AGE | Unified PG, Cypher queries | `pg-age` |
| **PgSqlGraphStore** | Pure PostgreSQL SQL | Max compatibility, no extensions | `pg-sql` |
| **DuckDbGraphStore** | DuckDB (default) | Embedded, single-file | `duckdb` |
| **InMemoryGraphStore** | JGraphT | Development, <100K nodes | `in-memory` |

```yaml
# Select graph store via configuration
cef:
  graph:
    store: neo4j  # neo4j | pg-age | pg-sql | duckdb | in-memory
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
# Minimal: DuckDB embedded + local Ollama (no Docker needed for DB)
# Just ensure Ollama is running locally: ollama serve

# PostgreSQL + pgvector (vector store)
docker-compose up -d postgres

# Neo4j (graph store) + PostgreSQL (vector store)
docker-compose up -d neo4j postgres

# PostgreSQL with Apache AGE (graph + vector in one)
docker-compose --profile age up -d postgres-age

# All services (Neo4j + PostgreSQL + AGE + MinIO)
docker-compose --profile age --profile minio up -d
```

### 3. Run Framework Tests

```bash
# Run comprehensive test suite (178+ tests)
cd cef-framework
mvn test

# Tests include:
# - Neo4j integration (18 tests, Testcontainers)
# - PostgreSQL AGE integration (18 tests, Testcontainers)
# - PostgreSQL SQL integration (18 tests, Testcontainers)
# - Security/validation tests (49+ tests)
# - Thread safety tests (21 tests)
# - Medical/Financial domain benchmarks
```

### 4. Access Services

- **Ollama**: http://localhost:11434/api/tags
- **Neo4j Browser** (if enabled): http://localhost:7474
- **PostgreSQL** (if enabled): localhost:5432
- **PostgreSQL AGE** (if enabled): localhost:5433
- **MinIO Console** (if enabled): http://localhost:9001

---

## Project Structure

```
ced/
├── cef-framework/          # Core framework (JAR library)
│   ├── src/main/java/      # ORM implementation
│   │   └── org/ddse/ml/cef/
│   │       ├── domain/     # Node, Edge, Chunk, RelationType
│   │       ├── api/        # KnowledgeIndexer, KnowledgeRetriever
│   │       ├── graph/      # GraphStore implementations (v0.6)
│   │       │   ├── Neo4jGraphStore.java
│   │       │   ├── PgAgeGraphStore.java
│   │       │   ├── PgSqlGraphStore.java
│   │       │   └── InMemoryGraphStore.java
│   │       ├── config/     # Auto-configuration (v0.6)
│   │       ├── security/   # Auth, sanitization, audit (v0.6)
│   │       ├── health/     # Health indicators (v0.6)
│   │       ├── retriever/  # Pattern-based retrieval
│   │       └── parser/     # Domain transformation
│   ├── src/test/java/      # 178+ integration tests
│   └── pom.xml
│
├── ddse/                   # Architecture Decision Records
│   ├── v0.6/IDR-004.md     # v0.6 Implementation Decision
│   ├── ARCHITECTURE.md     # Technical architecture
│   └── requirements.md     # Specifications
│
├── docker-compose.yml      # Neo4j, PostgreSQL, AGE, MinIO
├── USER_GUIDE.md           # ORM integration guide
├── RELEASE_NOTES.md        # Version history
├── KNOWN_ISSUES.md         # Limitations and gaps
├── QUICKSTART.md           # Getting started
└── pom.xml                 # Parent POM
```

---

## Configuration

### Store Selection (v0.6)

CEF v0.6 uses **two independent store configurations**:
- `cef.graph.store` - Where relationships (nodes/edges) are stored
- `cef.vector.store` - Where vector embeddings (chunks) are stored

```yaml
cef:
  graph:
    store: duckdb  # duckdb | in-memory | neo4j | pg-sql | pg-age
  vector:
    store: duckdb  # duckdb | in-memory | neo4j | postgresql
```

### PostgreSQL: Two Graph Storage Options

PostgreSQL supports **two different** graph storage approaches:

| Option | Config | Extension Required | Use Case |
|--------|--------|-------------------|----------|
| **pg-sql** | `cef.graph.store=pg-sql` | None | Maximum compatibility, SQL adjacency tables |
| **pg-age** | `cef.graph.store=pg-age` | Apache AGE | Cypher queries on PostgreSQL |

Both use `cef.vector.store=postgresql` for pgvector embeddings.

### Tested Backend Combinations

| Profile | Graph Store | Vector Store | Infrastructure |
|---------|-------------|--------------|----------------|
| **in-memory** | `in-memory` | `in-memory` | None |
| **duckdb** | `duckdb` | `duckdb` | None (embedded) |
| **neo4j** | `neo4j` | `neo4j` | Neo4j 5.11+ |
| **pg-sql** | `pg-sql` | `postgresql` | PostgreSQL + pgvector |
| **pg-age** | `pg-age` | `postgresql` | PostgreSQL + AGE + pgvector |

### Deployment Patterns

```yaml
# Development (zero infrastructure)
cef:
  graph:
    store: in-memory
  vector:
    store: in-memory

# Default (embedded DuckDB for both)
cef:
  graph:
    store: duckdb
  vector:
    store: duckdb

# Production: Neo4j for both (unified)
cef:
  graph:
    store: neo4j
  vector:
    store: neo4j

# Production: PostgreSQL unified (AGE + pgvector)
cef:
  graph:
    store: pg-age  # or pg-sql
  vector:
    store: postgresql

# Hybrid: Neo4j graph + PostgreSQL vectors
cef:
  graph:
    store: neo4j
  vector:
    store: postgresql
```

### Neo4j Configuration

```yaml
cef:
  graph:
    store: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: cef_password
      database: neo4j
  vector:
    store: neo4j  # Uses Neo4j vector indexes
```

### PostgreSQL Configuration

```yaml
cef:
  graph:
    store: pg-sql  # or pg-age for Cypher support
    postgres:
      graph-name: cef_graph
      max-traversal-depth: 5
  vector:
    store: postgresql  # Uses pgvector extension

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

### Default (DuckDB + Ollama)

```yaml
cef:
  graph:
    store: duckdb
  vector:
    store: duckdb
    dimension: 768
  
  llm:
    default-provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3.2:3b
```

### Resilience Configuration (v0.6)

```yaml
cef:
  resilience:
    embedding:
      retry:
        max-attempts: 3
        wait-duration: 1s
      circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
      timeout: 30s
```

### Security Configuration (v0.6)

```yaml
cef:
  security:
    enabled: true  # Default: false (opt-in)
    api-key:
      enabled: true
      header-name: X-API-Key
      keys:
        - name: dev-key
          key: ${CEF_API_KEY}
          roles: [READ, WRITE]
```

### Optional (PostgreSQL + pgvector)

```yaml
cef:
  graph:
    store: pg-sql
  vector:
    store: postgresql

spring:
  # JDBC for Graph Store
  datasource:
    url: jdbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
    driver-class-name: org.postgresql.Driver
  
  # R2DBC for Vector Store
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

---

## Usage

### 1. Framework Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>0.6</version>
</dependency>
```

**Note:** v0.6 tested with Neo4j 5.x, PostgreSQL 16 (pgvector, AGE), DuckDB, vLLM (Qwen3-Coder-30B), and Ollama (nomic-embed-text). See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for gaps.

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
- [QUICKSTART.md](QUICKSTART.md) - Get started in 5 minutes
- [RELEASE_NOTES.md](RELEASE_NOTES.md) - Version v0.6 release notes
- [KNOWN_ISSUES.md](KNOWN_ISSUES.md) - Limitations and production gaps
- [ddse/ARCHITECTURE.md](ddse/ARCHITECTURE.md) - Technical architecture
- [ddse/v0.6/IDR-004.md](ddse/v0.6/IDR-004.md) - v0.6 Implementation Decision Record
- [ddse/EVALUATION_SUMMARY.md](ddse/EVALUATION_SUMMARY.md) - Benchmark analysis (60-220% improvement)

---

## Technology Stack

- **Java 17** - Language
- **Spring Boot 3.3.5** - Application framework
- **Spring AI 1.0.0-M4** - LLM integration
- **Spring WebFlux** - Reactive web
- **Spring Data R2DBC** - Reactive database
- **Resilience4j** - Fault tolerance (v0.6)
- **JGraphT 1.5.2** - In-memory graph
- **Neo4j Driver 5.x** - Native graph database (v0.6)
- **Apache AGE** - PostgreSQL graph extension (v0.6)
- **ANTLR 4.13.1** - Parser generator
- **DuckDB 1.1.3** - Default embedded database
- **PostgreSQL 16** - External database (with pgvector)
- **Apache PDFBox 3.0.3** - PDF processing
- **Testcontainers** - Integration testing (v0.6)

---

## License

MIT License

Copyright (c) 2024-2025 DDSE Foundation

See [LICENSE](LICENSE) file for full license text.

---

## Contributing

Contributions are welcome! Please:

1. Run the test suite (`mvn test` in cef-framework)
2. Report issues with detailed logs and reproduction steps
3. Submit pull requests with test coverage
4. Review [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for areas needing work:
   - PgAGE query parameterization
   - Health indicators for all backends
   - Resilience patterns for graph/vector stores
   - Security hardening

For questions, contact DDSE Foundation at https://ddse-foundation.github.io/

---

## Authors

- **Mahmudur R Manna (mrmanna)** - Founder and Principal Architect, [DDSE Foundation](https://ddse-foundation.github.io/)

---

## About DDSE Foundation

This framework is developed by the **DDSE Foundation** (Decision-Driven Software Engineering), an open-source initiative advancing principled approaches to software architecture and engineering.
