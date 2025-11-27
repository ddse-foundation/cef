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

See [docs/EVALUATION_SUMMARY.md](docs/EVALUATION_SUMMARY.md) for complete benchmark analysis.

---

## Option 2: With PostgreSQL (Demonstrates Database Agnosticism)

### Step 1: Start Services with PostgreSQL

```bash
docker-compose --profile postgres up -d
```

### Step 2: Configure Tests for PostgreSQL

Create `cef-framework/src/test/resources/application-postgres.yml`:

```yaml
cef:
  database:
    type: postgresql
    postgresql:
      enabled: true

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/cef_db
    username: cef_user
    password: cef_password
```

### Step 3: Run Tests with PostgreSQL

```bash
mvn clean install
cd cef-framework
mvn test -Dspring.profiles.active=postgres
```

---

## Option 3: Full Setup (PostgreSQL + MinIO + Ollama)

For the complete demonstration including blob storage.

### Step 1: Start All Services

```bash
docker-compose --profile postgres --profile minio up -d
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

