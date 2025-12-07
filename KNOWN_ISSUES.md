# Known Issues

**Version:** v0.6 (Research)  
**Last Updated:** December 7, 2025

---

## Status Snapshot (v0.6)

- Scope: Research-grade only. Not production-ready.
- New backends: Neo4j, PostgreSQL (pg-sql), PostgreSQL + Apache AGE (pg-age), DuckDB, In-Memory.
- New vector stores: DuckDB, PostgreSQL pgvector, Neo4j vector index, In-Memory.

---

## Open Issues / Gaps

### Security
- HTTP security is opt-in (API-key/basic). JWT/OAuth2 not wired by default. Endpoints are not protected unless explicitly enabled.
- Store-level sanitization not enforced everywhere: PgAGE builds Cypher strings with manual escaping; needs parameterization or sanitizer at the boundary.
- Audit logging only wired for security events; destructive store ops (delete/clear) are not audited.

### Resilience
- Resilience patterns (retry/circuit-breaker/timeout) are applied only to embeddings. Graph/vector/database operations lack consistent timeouts/retries/circuit breakers.
- No guardrails for runaway traversals beyond configurable depth caps; no per-call timeout on graph store methods.

### Observability
- Health indicators/metrics exist only for in-memory graph and embeddings. No health checks/metrics for Neo4j, PgSql, PgAge, DuckDB, pgvector chunk store.
- No slow-query logging or tracing for graph/vector operations.

### Graph Stores
- PgAGE uses hand-built Cypher with manual escaping; risk of injection or malformed queries.
- PgSql/PgAge lack per-call timeout and retry wrappers.
- InMemoryGraphStore stubs: `findEdgesByRelationType` and `getNeighborsByRelationType` return empty; not feature-complete for dev scenarios.

### Configuration / Defaults
- Security disabled by default; production deployments must opt in and configure keys/claims explicitly.
- In-memory thread-safe mode is opt-in (`cef.graph.thread-safe=true`); default is non-thread-safe.
- Neo4j health/metrics not exposed; connection pool configuration is applied but not observed.

### Testing / Execution
- Live-stack integration tests (vLLM/Ollama/Neo4j/Postgres/AGE) run by default; they require external services. CI should exclude or gate them if infra is absent.
- No automated validation of health/metrics endpoints for each backend.

---

## Testing Status

### ✅ Tested Configurations

The following configurations are exercised by integration tests (Testcontainers or live stack):

- **Graph/DB stores:** DuckDB (default), PostgreSQL pg-sql, PostgreSQL + Apache AGE (pg-age), Neo4j, In-Memory.
- **Vector stores:** DuckDB VSS, PostgreSQL pgvector, Neo4j vector index, In-Memory.
- **LLM/Embeddings:** vLLM (OpenAI-compatible, Qwen3-Coder-30B), Ollama embeddings (nomic-embed-text).
- **OS:** Linux.

---

## Known Issues

### 1) Security defaults off
- HTTP security is opt-in (API-key/basic only). JWT/OAuth2 not wired by default. Endpoints are open unless enabled.
- Store-level sanitization not enforced uniformly; PgAGE uses manual Cypher escaping.
- Destructive store operations (delete/clear) not audited.

### 2) Resilience gaps
- Retry/circuit-breaker/timeout applied only to embeddings. Graph/vector/database operations lack consistent resilience.
- No per-call timeouts on PgSql/PgAge/Neo4j/DuckDB queries; traversal guardrails limited to depth caps.

### 3) Observability gaps
- No health checks/metrics for Neo4j, PgSql, PgAge, DuckDB, pgvector chunk store.
- No slow-query logging or tracing on graph/vector calls.

### 4) Graph store limitations
- PgAGE Cypher built via string concatenation; risk of injection/malformed queries.
- InMemoryGraphStore missing `findEdgesByRelationType` and `getNeighborsByRelationType` implementations.
- In-memory thread safety is opt-in; default remains non-thread-safe.

### 5) Vector search constraints
- DuckDB VSS uses brute-force similarity (no ANN); performance drops beyond ~50K chunks.

### 6) Postgres schema management
- pg-sql/pgvector schemas require manual migration (no automatic DDL); Flyway/Liquibase recommended.

### 7) Embedding throughput
- Ollama embedding calls are single-threaded; large ingest is slow.

### 8) Context size
- Large subgraphs can exceed LLM context; no automatic summarization/truncation beyond depth limits.

**Status:** v0.6 delivers foundational observability; enhanced coverage planned for v0.7

---

### 9. Multi-tenancy Support

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

1. Check the [USER_GUIDE.md](USER_GUIDE.md) Troubleshooting section
2. Review [ddse/ARCHITECTURE.md](ddse/ARCHITECTURE.md) for design constraints
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
