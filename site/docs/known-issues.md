# Known Issues

**Version:** beta-0.5  
**Last Updated:** November 27, 2025

---

## Testing Status

### ✅ Tested Configurations

The following configurations have been thoroughly tested and validated:

- **Database:** DuckDB (embedded)
- **LLM Provider:** vLLM with Qwen3-Coder-30B-A3B-Instruct-FP8
- **Embedding Model:** Ollama with nomic-embed-text (768 dimensions)
- **Graph Store:** JGraphT (in-memory)
- **Operating Systems:** Linux

### ⚠️ Untested Configurations

The following configurations are implemented and available but **not yet tested in production**:

#### Databases
- **PostgreSQL** - Configuration available, schema provided, but not fully tested
  - R2DBC reactive driver configured
  - pgvector extension support included
  - Migration scripts available
  - **Status:** Needs integration testing

#### LLM Providers
- **OpenAI** - Client factory implemented, configuration available
  - GPT-4, GPT-3.5 Turbo support
  - **Status:** Needs API key testing
  
- **Ollama (LLM)** - Configuration available for Llama models
  - Llama 3.2, Llama 3.1 support
  - **Status:** Needs comprehensive testing

#### Vector Stores
- **Qdrant** - Interface implemented, configuration available
  - **Status:** Needs deployment and integration testing
  
- **Pinecone** - Interface implemented, configuration available
  - **Status:** Needs API key and integration testing

#### Graph Stores
- **Neo4j** - Interface defined, configuration available
  - Cypher query support planned
  - **Status:** Requires Neo4j instance and integration testing

---

## Known Issues

### 1. Memory Management (JGraphT)

**Issue:** In-memory graph store (JGraphT) may experience memory pressure with large knowledge bases.

**Details:**
- Recommended maximum: 100,000 nodes
- Memory usage: ~350MB for 100K nodes, 500K edges
- No automatic pagination or lazy loading

**Workaround:**
- Monitor heap usage with `-Xmx` settings
- Consider Neo4j for graphs >100K nodes
- Implement periodic graph pruning for time-bound data

**Status:** By design - JGraphT is optimized for fast in-memory operations

---

### 2. Embedding Generation Performance

**Issue:** Batch embedding generation can be slow for large document sets.

**Details:**
- Single-threaded embedding calls to Ollama
- ~200ms per embedding with nomic-embed-text
- Initial index of 10,000 chunks takes ~30-40 minutes

**Workaround:**
- Use batch indexing during off-peak hours
- Consider pre-generating embeddings offline
- Increase Ollama concurrency settings

**Status:** Planned for optimization in v0.6

---

### 3. PostgreSQL Schema Auto-Creation

**Issue:** R2DBC does not automatically create tables on startup like JPA.

**Details:**
- Schema files provided in `src/main/resources/`
- Requires manual execution or Flyway/Liquibase integration

**Workaround:**
- Run `schema-postgresql.sql` manually before first startup
- Or configure Flyway migration (example in docs)

**Status:** Documentation updated, auto-migration planned for v1.0

---

### 4. Vector Search with DuckDB

**Issue:** DuckDB vector similarity search uses brute-force comparison (no HNSW index).

**Details:**
- Performance degrades with >50,000 chunks
- No approximate nearest neighbor (ANN) support in DuckDB VSS extension

**Workaround:**
- Use PostgreSQL with pgvector for large-scale deployments
- Implement result caching for frequent queries

**Status:** DuckDB limitation - consider PostgreSQL for production

---

### 5. Concurrent Indexing

**Issue:** Multiple concurrent indexing operations may cause race conditions in graph updates.

**Details:**
- JGraphT in-memory store is not thread-safe by default
- Edge additions during concurrent node updates may be inconsistent

**Workaround:**
- Use sequential indexing for initial data load
- Implement application-level locking for concurrent updates

**Status:** Thread-safe wrapper planned for v0.6

---

### 6. Context Window Token Limits

**Issue:** Large relationship subgraphs may exceed LLM context windows.

**Details:**
- No automatic context pruning or summarization
- Deep graph traversals (depth >3) can generate excessive context

**Workaround:**
- Limit traversal depth to 2-3 hops
- Use `topK` parameter to control result size
- Implement custom context filtering in application layer

**Status:** Intelligent context truncation planned for v0.7

---

### 7. Relationship Type Validation

**Issue:** No runtime validation that edges match defined RelationType schemas.

**Details:**
- Framework accepts any relation type string
- Semantic hints (HIERARCHY, CAUSALITY) are advisory only

**Workaround:**
- Implement application-level validation
- Use strict entity builders with type checking

**Status:** Schema validation framework planned for v0.8

---

### 8. Docker Compose Networking

**Issue:** Ollama container may not be accessible from host on some Docker Desktop versions.

**Details:**
- DNS resolution of `ollama:11434` fails from host machine
- WSL2 networking issues on Windows

**Workaround:**
- Use `localhost:11434` instead of `ollama:11434` in application.yml
- Or add `127.0.0.1 ollama` to /etc/hosts

**Status:** Documentation updated with troubleshooting steps

---

### 9. Query Performance Monitoring

**Issue:** Limited built-in query performance metrics.

**Details:**
- No automatic slow query logging
- Graph traversal metrics not exposed via actuator

**Workaround:**
- Enable DEBUG logging for `org.ddse.ml.cef`
- Use Spring Boot Actuator with custom metrics

**Status:** Enhanced observability planned for v0.6

---

### 10. Multi-tenancy Support

**Issue:** No built-in tenant isolation for knowledge graphs.

**Details:**
- Single shared graph store per application instance
- No tenant-specific caching or partitioning

**Workaround:**
- Deploy separate application instances per tenant
- Implement application-level filtering with tenant_id in node properties

**Status:** Multi-tenancy patterns documented in USER_GUIDE.md

---

## Reporting Issues

If you encounter issues not listed here:

1. Check the [User Guide](user-guide) Troubleshooting section
2. Review [Architecture](architecture) for design constraints
3. Enable DEBUG logging: `logging.level.org.ddse.ml.cef=DEBUG`
4. Report via GitHub Issues with:
   - Configuration details (database, LLM provider, models)
   - Error logs and stack traces
   - Steps to reproduce
   - Expected vs actual behavior

---

## Testing Contributions Welcome

We welcome community testing and feedback on untested configurations:

- **PostgreSQL integration** - Production deployment reports
- **OpenAI provider** - API compatibility testing
- **Neo4j graph store** - Large-scale graph performance
- **Qdrant/Pinecone** - Vector store benchmarking
- **Windows/macOS** - Platform compatibility

Contact DDSE Foundation at https://ddse-foundation.github.io/ for contribution guidelines.

---

**DDSE Foundation** - [https://ddse-foundation.github.io/](https://ddse-foundation.github.io/)
