# CEF Quick Start Guide

This guide will help you set up and run the CEF (Context Engineering Framework) project.

**CEF is an ORM for LLM context engineering** - just as Hibernate abstracts databases for transactional data, CEF abstracts knowledge stores for LLM context. You define knowledge models (entities, relationships), and CEF handles persistence, caching, and intelligent context assembly.

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

---

## Option 1: Quick Start (Recommended for First Run)

This uses DuckDB (embedded, no external database needed) and Ollama (local LLM).

### Step 1: Clone the Repository

```bash
git clone <repository-url>
cd ced
```

### Step 2: Start Ollama

```bash
docker-compose up -d
```

This starts only Ollama. DuckDB will be embedded in the application.

### Step 3: Build and Test

```bash
# Build framework
mvn clean install

# Run comprehensive test suite
cd cef-framework
mvn test

# Tests include:
# - Medical domain: 177 nodes, 455 edges
# - Financial domain: SAP-simulated data
# - Benchmarks: Knowledge Model vs Vector-Only
```

### Step 4: Verify Services

```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Verify test execution
ls -l cef-framework/target/surefire-reports/
```

### Step 5: Understanding the ORM Layer

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

See [Benchmark Analysis](benchmarks) for complete benchmark analysis.

---

## Option 2: With Neo4j (Production Graph Store)

> **Note:** Neo4j support is fully tested in v0.6 with 18 integration tests via Testcontainers.

### Step 1: Start Neo4j

```bash
docker-compose up -d neo4j
```

### Step 2: Configure for Neo4j

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
    store: neo4j  # Neo4j 5.11+ has native vector indexes
```

### Step 3: Access Neo4j Browser

Open http://localhost:7474 and login with neo4j/cef_password.

---

## Option 3: With PostgreSQL + pgvector

> **Note:** PostgreSQL support is fully tested in v0.6 with two modes: `pg-sql` (pure SQL) and `pg-age` (Apache AGE Cypher).

### Step 1: Start PostgreSQL

```bash
docker-compose up -d postgres
```

### Step 2: Configure for PostgreSQL (pg-sql mode)

```yaml
cef:
  graph:
    store: pg-sql  # Pure SQL adjacency tables
  vector:
    store: postgresql  # pgvector for embeddings

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

### Step 3: Run Tests with PostgreSQL

```bash
cd cef-framework
mvn test -Dspring.profiles.active=pg-sql
```

---

## Option 4: PostgreSQL with Apache AGE (Cypher on PostgreSQL)

> **Note:** Apache AGE allows Cypher queries on PostgreSQL. Fully tested in v0.6.

### Step 1: Start PostgreSQL + AGE

```bash
docker-compose --profile age up -d postgres-age
```

### Step 2: Configure for AGE

```yaml
cef:
  graph:
    store: pg-age  # Apache AGE for Cypher queries
    postgres:
      graph-name: cef_graph
  vector:
    store: postgresql

spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/cef_graph_db
    username: cef_user
    password: cef_password
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

---

## Option 5: Full Setup (All Services)

### Step 1: Start All Services

```bash
docker-compose --profile age --profile minio up -d
```

### Step 2: Configure Tests for MinIO

Create `cef-framework/src/test/resources/application-minio.yml`:

```yaml
cef:
  database:
    type: postgresql
    postgresql:
      enabled: true
  
  datasources:
    blob-storage:
      enabled: true
      endpoint: http://localhost:9000
      bucket: test-documents
      access-key: minioadmin
      secret-key: minioadmin
```

### Step 3: Run Tests with MinIO

```bash
mvn clean install
cd cef-framework
mvn test -Dspring.profiles.active=minio
```

### Step 4: Access Services

- **Example App**: http://localhost:8080
- **Ollama**: http://localhost:11434
- **MinIO Console**: http://localhost:9001 (admin/minioadmin)
- **PostgreSQL**: localhost:5432 (cef_user/cef_password)

---

## Verify Installation

### 1. Check Services

```bash
# Check Ollama
curl http://localhost:11434/api/tags

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
   - See the [Benchmarks](benchmarks.md) page for published reports
   - Image: `static/img/benchmark_comparison.png`
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
