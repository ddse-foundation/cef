# Known Issues

**Version:** 0.6  
**Last Updated:** December 7, 2025

---

## Testing Status

### ✅ Tested Configurations (v0.6)

The following configurations have been thoroughly tested with **178+ integration tests**:

**Graph Stores (5 backends):**
- **Neo4jGraphStore** - Neo4j 5.x Community (18 tests via Testcontainers)
- **PgAgeGraphStore** - PostgreSQL + Apache AGE (18 tests via Testcontainers)
- **PgSqlGraphStore** - Pure PostgreSQL SQL (18 tests via Testcontainers)
- **DuckDbGraphStore** - DuckDB embedded (default)
- **InMemoryGraphStore** - JGraphT in-memory

**Vector Stores (4 backends):**
- **Neo4jChunkStore** - Neo4j vector indexes
- **R2dbcChunkStore** - PostgreSQL + pgvector (reactive R2DBC)
- **DuckDbChunkStore** - DuckDB VSS extension (default)
- **InMemoryChunkStore** - ConcurrentHashMap

**LLM & Embeddings:**
- **vLLM** with Qwen3-Coder-30B-A3B-Instruct-FP8
- **Ollama** with nomic-embed-text (768 dimensions)

### ⚠️ Configured but Untested

#### LLM Providers
- **OpenAI** - Client factory implemented, configuration available
  - GPT-4, GPT-3.5 Turbo support
  - **Status:** Needs API key testing

#### Vector Stores
- **Qdrant** - Interface implemented, configuration available
  - **Status:** Needs deployment and integration testing
  
- **Pinecone** - Interface implemented, configuration available
  - **Status:** Needs API key and integration testing

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

**Issue:** ✅ **RESOLVED in v0.6** - Multiple concurrent indexing operations are now thread-safe.

**Details:**
- `ThreadSafeKnowledgeGraph.java` wraps JGraphT with ReadWriteLock
- 21 concurrent tests including stress tests validate thread safety
- Opt-in via `cef.graph.thread-safe=true`

**Status:** Resolved in v0.6

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
