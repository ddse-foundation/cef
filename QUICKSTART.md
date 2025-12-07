# CEF Quick Start Guide

**Version:** v0.6 (Research)

This guide will help you set up and run the CEF (Context Engineering Framework) project.

**CEF is an ORM for LLM context engineering** - just as Hibernate abstracts databases for transactional data, CEF abstracts knowledge stores for LLM context. You define knowledge models (entities, relationships), and CEF handles persistence, caching, and intelligent context assembly.

## What's New in v0.6

- **5 Graph Store Backends**: Neo4j, PostgreSQL (AGE + SQL), DuckDB, In-Memory
- **Resilience**: Retry, circuit breaker, timeout for embeddings
- **Security**: API-key auth, input sanitization (opt-in)
- **178+ Integration Tests**: Real infrastructure via Testcontainers

## Prerequisites

- **Java 17 or higher**
  ```bash
  java -version  # Should show 17+
  ```

- **Maven 3.8+**
  ```bash
  mvn -version
  ```

- **Docker & Docker Compose**
  ```bash
  docker --version
  docker-compose --version
  ```

- **Ollama** (for embeddings)
  ```bash
  ollama serve  # Run locally, or use Docker
  ollama pull nomic-embed-text
  ```

---

## Option 1: Quick Start (DuckDB - Recommended)

Uses DuckDB (embedded, no external database needed) and Ollama (local LLM).

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd ced
```

### Step 2: Build and Test

```bash
# Build framework
mvn clean install

# Run comprehensive test suite (178+ tests)
cd cef-framework
mvn test

# Tests include:
# - Neo4j integration (18 tests, Testcontainers)
# - PostgreSQL AGE/SQL integration (36 tests)
# - Security/validation tests (49+ tests)
# - Medical/Financial domain benchmarks
```

### Step 3: Verify Results

```bash
# Check test execution
ls -l target/surefire-reports/
cat target/surefire-reports/*.txt | grep -E "Tests run|PASSED|FAILED"
```

---

## Option 2: With Neo4j (Production Graph Store)

### Step 1: Start Neo4j

```bash
docker-compose up -d neo4j
```

### Step 2: Configure for Neo4j

Create `application-neo4j.yml`:

```yaml
cef:
  graph:
    store: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: cef_password
```

### Step 3: Access Neo4j Browser

Open http://localhost:7474 and login with neo4j/cef_password.

---

## Option 3: With PostgreSQL + pgvector (Pure SQL Graph)

### Step 1: Start PostgreSQL

```bash
docker-compose up -d postgres
```

### Step 2: Configure for PostgreSQL (pg-sql)

This uses pure SQL adjacency tables for graph storage (no extensions needed):

```yaml
cef:
  graph:
    store: pg-sql  # SQL adjacency tables
  vector:
    store: postgresql  # pgvector for embeddings
    
spring:
  # JDBC for Graph Store
  datasource:
    url: jdbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
    driver-class-name: org.postgresql.Driver
  
  # R2DBC for Vector Store (reactive)
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

---

## Option 4: PostgreSQL with Apache AGE (Cypher on PostgreSQL)

AGE uses a **separate PostgreSQL instance** for graph (Cypher) while sharing pgvector for vectors.

### Step 1: Start PostgreSQL + AGE

```bash
docker-compose --profile age up -d postgres-age
```

### Step 2: Configure for AGE

```yaml
cef:
  graph:
    store: pg-age  # Apache AGE (Cypher queries)
    postgres:
      graph-name: cef_graph
  vector:
    store: postgresql  # pgvector (separate instance)

spring:
  # JDBC for Graph Store (AGE on port 5433)
  datasource:
    url: jdbc:postgresql://localhost:5433/cef_graph_db
    username: cef_user
    password: cef_password
    driver-class-name: org.postgresql.Driver
  
  # R2DBC for Vector Store (pgvector on port 5432)
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

### Step 3: Access PostgreSQL AGE

```bash
psql -h localhost -p 5433 -U cef_user -d cef_graph_db
```

---

## Option 5: Full Stack (All Services)

### Step 1: Start Everything

```bash
docker-compose --profile age --profile minio up -d
```

### Step 2: Available Services

| Service | URL | Credentials |
|---------|-----|-------------|
| **Neo4j Browser** | http://localhost:7474 | neo4j / cef_password |
| **PostgreSQL** | localhost:5432 | cef_user / cef_password |
| **PostgreSQL AGE** | localhost:5433 | cef_user / cef_password |
| **MinIO Console** | http://localhost:9001 | minioadmin / minioadmin |
| **Ollama API** | http://localhost:11434 | - |

---

## Understanding the ORM Layer

The test suite demonstrates ORM patterns with real scenarios:

**Medical Domain Test (MedicalBenchmarkTest.java):**
```java
// 177 nodes: 150 patients, 5 conditions, 7 medications, 15 doctors
// 455 edges: Patient-Condition, Patient-Medication, Doctor-Patient

// Pattern-based retrieval (multi-hop reasoning)
GraphPattern pattern = GraphPattern.multiHop(
    "Find contraindications",
    List.of(
        new TraversalStep("HAS_CONDITION", "*", ...),
        new TraversalStep("PRESCRIBED_MEDICATION", "*", ...)
    )
);

// Result: 120% more chunks than vector-only search
```

**Financial Domain Test (SapBenchmarkTest.java):**
```java
// SAP-simulated data: Vendors, Materials, Invoices
// Complex procurement workflows
// Enterprise relationship patterns
```

See [ddse/EVALUATION_SUMMARY.md](ddse/EVALUATION_SUMMARY.md) for complete benchmark analysis.

---

## Verify Installation

### 1. Check Services

```bash
# Check Ollama
curl http://localhost:11434/api/tags

# Check Neo4j (if running)
curl http://localhost:7474

# Check PostgreSQL (if running)
docker ps | grep cef-postgres

# Check MinIO (if running)
docker ps | grep cef-minio
```

### 2. Check Application

```bash
curl http://localhost:8080/actuator/health
```

Expected:
```json
{"status":"UP"}
```

### 3. Check Ollama Models

```bash
docker exec -it cef-ollama ollama list
```

You should see `nomic-embed-text` listed (used for embeddings in tests).

**Note:** Tests use vLLM with Qwen3-Coder-30B for generation, not Ollama LLMs.

---

## Troubleshooting

### Ollama Model Not Found

Tests use Ollama for embeddings only (nomic-embed-text):

```bash
# Pull embedding model
docker exec -it cef-ollama ollama pull nomic-embed-text
```

**Note:** Tests use vLLM (Qwen3-Coder-30B) for generation, which requires separate installation.

### Port Already in Use

If vLLM or Ollama ports are already in use:

Edit test configuration:
```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8002  # Change vLLM port
    ollama:
      base-url: http://localhost:11435  # Change Ollama port
```

### PostgreSQL Connection Issues

Check PostgreSQL is running:
```bash
docker ps | grep postgres
```

Check logs:
```bash
docker logs cef-postgres
```

### DuckDB File Lock

If you get "database is locked" errors:

```bash
# Stop the application
# Remove the lock file
rm data/cef.duckdb.wal

# Restart
mvn spring-boot:run
```

---

## Next Steps

1. **Review Benchmark Results**
   - See `docs/EVALUATION_SUMMARY.md` for detailed analysis
   - View `docs/benchmark_comparison.png` for performance charts
   - Knowledge Model shows 60-220% improvement over vector-only

2. **Explore Test Suite**
   - Medical domain: `cef-framework/src/test/java/org/ddse/ml/cef/benchmark/MedicalBenchmarkTest.java`
   - Financial domain: `cef-framework/src/test/java/org/ddse/ml/cef/benchmark/SapBenchmarkTest.java`
   - Real-world patterns and multi-hop reasoning examples

3. **Define Your Knowledge Models**
   - Review `USER_GUIDE.md` for ORM integration patterns
   - Understand Node (entity) and Edge (relationship) concepts
   - Study test data generators for reference implementations

4. **Read Documentation**
   - `USER_GUIDE.md` - Complete ORM integration guide
   - `docs/ARCHITECTURE.md` - ORM architecture and design
   - `docs/ADR-002.md` - Technical implementation details
   - `docs/requirements.md` - ORM philosophy and specifications

---

## Quick Reference

### Backend Combinations

| Profile | Graph Store | Vector Store | Docker Services |
|---------|-------------|--------------|-----------------|
| **in-memory** | `in-memory` | `in-memory` | None required |
| **duckdb** | `duckdb` | `duckdb` | None (embedded) |
| **neo4j** | `neo4j` | `neo4j` | `docker-compose up -d neo4j` |
| **pg-sql** | `pg-sql` | `postgresql` | `docker-compose up -d postgres` |
| **pg-age** | `pg-age` | `postgresql` | `docker-compose --profile age up -d` |

### Default Configuration

| Component | Default | Optional Alternative |
|-----------|---------|---------------------|
| Database | DuckDB (embedded) | PostgreSQL |
| LLM | Ollama (local) | OpenAI, vLLM |
| Embedding | Ollama | OpenAI |
| Storage | FileSystem | S3/MinIO |

### Default Ports

| Service | Port |
|---------|------|
| Example App | 8080 |
| Ollama | 11434 |
| PostgreSQL | 5432 |
| MinIO API | 9000 |
| MinIO Console | 9001 |

### Default Credentials

| Service | Username | Password |
|---------|----------|----------|
| PostgreSQL | cef_user | cef_password |
| MinIO | minioadmin | minioadmin |

---

## Clean Restart

If you need to start fresh:

```bash
# Stop everything
docker-compose down

# Clean build artifacts and data
mvn clean
rm -rf data/

# Remove docker volumes
docker-compose down -v

# Start fresh
docker-compose up -d
mvn clean install
```

---

## Support

For issues or questions:
- Check `docs/` directory for detailed documentation
- Review ADR-002 and ADR-003 for architecture details
- Contact: mrmanna (@DDSE)

