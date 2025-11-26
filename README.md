# CEF - Context Engineering Framework

**Domain-Agnostic Context Engineering Framework for LLM Applications**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Overview

CEF is a reactive, domain-agnostic framework for context engineering in LLM applications. It provides:

- 🔗 **Graph-based reasoning** with JGraphT in-memory graph
- 🔍 **Hybrid search** (Graph + Vector Similarity + BM25)
- 📦 **Database agnostic** (DuckDB default, PostgreSQL optional)
- 🔌 **Multiple LLM providers** (OpenAI, Ollama, vLLM)
- 📄 **Parser system** (PDF, YAML, CSV, JSON with ANTLR support)
- ☁️ **Storage adapters** (FileSystem, S3/MinIO)
- ⚡ **Fully reactive** (Spring WebFlux + R2DBC)

**Author:** mrmanna  
**Organization:** DDSE  
**Date:** 2024

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    User Application                          │
│              (Defines: Nodes, Relations, Chunks)             │
└─────────────────────────────────────────────────────────────┘
                             │
                 ┌───────────┴───────────┐
                 │   Framework Interfaces │
                 │  1. KnowledgeIndexer   │
                 │  2. KnowledgeRetriever │
                 └────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│              CEF Framework Core                              │
│  • JGraphT In-Memory Graph                                   │
│  • Parser System (AbstractParser, ParserFactory)            │
│  • DataSource Adapters (FileSystem, S3/MinIO)               │
│  • LLM Client Factory (OpenAI, Ollama, vLLM)                │
│  • Dual Persistence (Database + In-Memory)                   │
└─────────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────────┐
│              Database (DuckDB / PostgreSQL)                  │
│  • Graph Schema (Node, Edge, RelationType)                   │
│  • Vector Schema (Chunk with embeddings)                     │
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
cd ced
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

### 3. Run Example Application

```bash
cd cef-example
mvn spring-boot:run
```

### 4. Access Services

- **Example App**: http://localhost:8080
- **Ollama**: http://localhost:11434
- **MinIO Console** (if enabled): http://localhost:9001
- **PostgreSQL** (if enabled): localhost:5432

---

## Project Structure

```
ced/
├── cef-framework/          # Core framework (JAR library)
│   ├── src/main/java/
│   │   └── org/ddse/ml/cef/
│   │       ├── domain/     # Node, Edge, Chunk, RelationType
│   │       ├── api/        # KnowledgeIndexer, KnowledgeRetriever
│   │       ├── parser/     # AbstractParser, ParserFactory
│   │       ├── datasource/ # FileSystem, BlobStorage adapters
│   │       ├── llm/        # LLM client factory
│   │       └── graph/      # JGraphT integration
│   └── pom.xml
│
├── cef-example/            # Medical domain example
│   ├── src/main/java/
│   │   └── org/ddse/ml/cef/example/
│   │       ├── domain/     # PatientDTO, DoctorDTO, etc.
│   │       ├── parser/     # MedicalPdfParser (ANTLR)
│   │       ├── api/        # REST controllers
│   │       └── config/     # Medical domain configuration
│   ├── src/main/antlr4/    # ANTLR grammars
│   ├── src/main/resources/
│   │   └── data/seed/      # Sample PDF prescriptions
│   └── pom.xml
│
├── docs/
│   ├── ADR-001.md          # Initial architecture (deprecated)
│   ├── ADR-002.md          # Framework core architecture
│   ├── ADR-003.md          # Medical example implementation
│   └── requirements.md     # Detailed requirements
│
├── docker-compose.yml      # Infrastructure services
├── pom.xml                 # Parent POM
└── README.md
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
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

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

### 4. Index Knowledge

```java
@Autowired
private KnowledgeIndexer indexer;

// Initialize with root nodes and relation types
indexer.initialize(rootNodes, relationTypes);

// Full index from data source
IndexResult result = indexer.fullIndex(dataSource);
```

### 5. Retrieve Context

```java
@Autowired
private KnowledgeRetriever retriever;

// Intelligent search with graph reasoning
SearchResult result = retriever.retrieve(
    RetrievalRequest.builder()
        .query("Show patients with diabetes")
        .depth(2)
        .topK(10)
        .build()
);
```

---

## Example: Medical Knowledge Assistant

The `cef-example` module demonstrates:

- ✅ PDF prescription parsing (ANTLR-based)
- ✅ Medical domain entities (Patient, Doctor, Condition, Medication)
- ✅ Graph reasoning (find patients → conditions → medications)
- ✅ Natural language queries
- ✅ React UI with chat and graph visualization
- ✅ Live LLM provider switching

### Try It

```bash
# Start services
docker-compose up -d

# Run example
cd cef-example
mvn spring-boot:run

# Chat at http://localhost:8080
# Query: "Show me all patients with diabetes and their medications"
```

---

## Documentation

- [ADR-002: Framework Architecture](docs/ADR-002.md) - Core framework design
- [ADR-003: Medical Example](docs/ADR-003.md) - Implementation guide
- [Requirements](docs/requirements.md) - Detailed specifications

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

Apache License 2.0

---

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## Authors

- **mrmanna** - Initial work - DDSE

---

## Presentation

This project is presented at **JUGBD (Java User Group Bangladesh)** meetup as a demonstration of context engineering for LLM applications.

**Topic:** Context Engineering with Java and DuckDB  
**Date:** 2024  
**Speaker:** mrmanna

