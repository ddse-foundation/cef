# CEF Quick Start Guide

This guide will help you set up and run the CEF (Context Engineering Framework) project.

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

### Step 3: Build and Run

```bash
# Build all modules
mvn clean install

# Run the example
cd cef-example
mvn spring-boot:run
```

**Or use the Makefile:**

```bash
make dev
```

### Step 4: Verify

- **Example App**: http://localhost:8080
- **Ollama**: http://localhost:11434/api/tags

---

## Option 2: With PostgreSQL (Demonstrates Database Agnosticism)

### Step 1: Start Services with PostgreSQL

```bash
docker-compose --profile postgres up -d
```

### Step 2: Update Configuration

Edit `cef-example/src/main/resources/application.yml`:

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

### Step 3: Build and Run

```bash
mvn clean install
cd cef-example
mvn spring-boot:run
```

**Or use Makefile:**

```bash
make dev-postgres
```

---

## Option 3: Full Setup (PostgreSQL + MinIO + Ollama)

For the complete demonstration including blob storage.

### Step 1: Start All Services

```bash
docker-compose --profile postgres --profile minio up -d
```

### Step 2: Enable MinIO in Configuration

Edit `cef-example/src/main/resources/application.yml`:

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
      bucket: medical-documents
      access-key: minioadmin
      secret-key: minioadmin
```

### Step 3: Build and Run

```bash
mvn clean install
cd cef-example
mvn spring-boot:run
```

**Or use Makefile:**

```bash
make dev-all
```

### Step 4: Access Services

- **Example App**: http://localhost:8080
- **Ollama**: http://localhost:11434
- **MinIO Console**: http://localhost:9001 (admin/minioadmin)
- **PostgreSQL**: localhost:5432 (cef_user/cef_password)

---

## Makefile Commands

The project includes a Makefile for convenience:

```bash
# Show all available commands
make help

# Full development setup
make dev              # DuckDB + Ollama
make dev-postgres     # PostgreSQL + Ollama
make dev-all          # PostgreSQL + MinIO + Ollama

# Docker management
make docker-up        # Start Ollama only
make docker-up-postgres  # Start with PostgreSQL
make docker-up-minio     # Start with MinIO
make docker-up-all       # Start all services
make docker-down      # Stop all services
make docker-clean     # Remove containers and volumes
make docker-logs      # Show logs

# Build and test
make build            # Build all modules
make test             # Run tests
make clean            # Clean build artifacts

# Run application
make run              # Run example app
make quick            # Quick run (skip tests)

# Check services
make check            # Check if services are running

# Ollama management
make ollama-pull-llama   # Pull llama3.2 model
make ollama-pull-nomic   # Pull nomic-embed-text model
make ollama-list         # List installed models

# Info
make info             # Show project info
```

---

## Verify Installation

### 1. Check Services

```bash
make check
```

Expected output:
```
✓ Ollama is running
✓ PostgreSQL is running (optional)
✓ MinIO is running (optional)
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
make ollama-list
```

You should see `llama3.2:3b` listed.

---

## Troubleshooting

### Ollama Model Not Found

If you get "model not found" errors:

```bash
# Pull the model manually
make ollama-pull-llama

# Or directly:
docker exec -it cef-ollama ollama pull llama3.2:3b
```

### Port Already in Use

If port 8080 is already in use:

Edit `cef-example/src/main/resources/application.yml`:
```yaml
server:
  port: 8081  # Change to available port
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

1. **Explore the API**
   - Visit http://localhost:8080/actuator/endpoints

2. **Try Example Queries**
   - See `docs/ADR-003.md` for medical domain examples

3. **Create Your Own Domain**
   - Use `cef-example` as a template
   - Define your own entities and parsers

4. **Read Documentation**
   - `docs/ADR-002.md` - Framework architecture
   - `docs/ADR-003.md` - Example implementation
   - `docs/requirements.md` - Detailed specs

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
make docker-down

# Clean build artifacts and data
make clean
rm -rf data/

# Remove docker volumes
make docker-clean

# Start fresh
make dev
```

---

## Support

For issues or questions:
- Check `docs/` directory for detailed documentation
- Review ADR-002 and ADR-003 for architecture details
- Contact: mrmanna (@DDSE)

