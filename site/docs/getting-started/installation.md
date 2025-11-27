---
sidebar_position: 1
---

# Installation

Get started with CEF Framework in your Spring Boot application.

## Prerequisites

- **Java 17 or higher** - CEF requires Java 17+ for modern language features
- **Maven 3.8+** or **Gradle 7+** - Build tool for dependency management
- **Spring Boot 3.3.5+** - CEF is built on Spring Boot 3.x
- **Docker** (optional) - For running infrastructure services (Ollama, PostgreSQL, etc.)

## Maven Installation

Add CEF Framework to your `pom.xml`:

```xml
<dependencies>
    <!-- CEF Framework Core -->
    <dependency>
        <groupId>org.ddse.ml</groupId>
        <artifactId>cef-framework</artifactId>
        <version>beta-0.5</version>
    </dependency>
    
    <!-- Spring Boot Starter (if not already included) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Spring Boot WebFlux (for reactive support) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
</dependencies>
```

## Gradle Installation

Add to your `build.gradle`:

```groovy
dependencies {
    implementation 'org.ddse.ml:cef-framework:beta-0.5'
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
}
```

## Infrastructure Setup

### Option 1: Quick Start with DuckDB (Embedded)

No external services needed! CEF comes with DuckDB embedded database:

```yaml
# application.yml
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

Start Ollama for embeddings:

```bash
# Install Ollama (Mac/Linux)
curl -fsSL https://ollama.com/install.sh | sh

# Pull embedding model
ollama pull nomic-embed-text

# Start Ollama service (runs on port 11434)
ollama serve
```

### Option 2: Persistent Setup with PostgreSQL (Experimental)

For advanced deployments with persistent storage (currently in alpha):

```bash
# Start PostgreSQL with pgvector extension
docker run -d \
  --name cef-postgres \
  -e POSTGRES_PASSWORD=cef_password \
  -e POSTGRES_USER=cef_user \
  -e POSTGRES_DB=cef_db \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

Configure CEF:

```yaml
# application.yml
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
  
  vector:
    store: postgres
    dimension: 768
```

### Option 3: Docker Compose (All Services)

Use the provided `docker-compose.yml`:

```bash
# Clone CEF repository
git clone https://github.com/ddse-foundation/cef.git
cd cef

# Start all services
docker-compose up -d

# Check status
docker-compose ps
```

Services included:
- **Ollama** (localhost:11434) - Embeddings and LLM
- **PostgreSQL** (localhost:5432) - Database with pgvector
- **MinIO** (localhost:9000) - Optional object storage

## Verify Installation

Create a simple Spring Boot application:

```java
package com.example.cef;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CefDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(CefDemoApplication.class, args);
    }
}
```

Run the application:

```bash
mvn spring-boot:run
```

Check logs for successful initialization:

```
INFO  o.d.ml.cef.config.CefAutoConfiguration - CEF Framework initialized
INFO  o.d.ml.cef.graph.InMemoryKnowledgeGraph - JGraphT graph store ready
INFO  o.d.ml.cef.storage.DuckDBVectorStore - DuckDB vector store initialized (dimension: 768)
```

## Next Steps

- Follow the [Quick Start Guide](../quickstart) to build your first knowledge model
- Learn about [Core Concepts](../concepts/knowledge-model) to understand CEF architecture
- Explore [Configuration Options](configuration) for advanced setups

## Troubleshooting

### Port Conflicts

If port 11434 (Ollama) or 5432 (PostgreSQL) are already in use:

```yaml
cef:
  llm:
    ollama:
      base-url: http://localhost:11435  # Change port
```

### Database Connection Issues

Verify PostgreSQL is running:

```bash
docker logs cef-postgres

# Test connection
psql -h localhost -U cef_user -d cef_db
```

### Embedding Model Not Found

Pull the embedding model manually:

```bash
ollama pull nomic-embed-text:latest
ollama list  # Verify installation
```

### More Help

- Check [Known Issues](../known-issues) for common problems
- Visit [GitHub Issues](https://github.com/ddse-foundation/cef/issues) for bug reports
- Join [GitHub Discussions](https://github.com/ddse-foundation/cef/discussions) for questions
