# Validation & Test Coverage (v0.6)

**178+ integration tests** map to concrete tests in `cef-framework/src/test/java`—no placeholder claims.

## v0.6 Test Summary

| Category | Tests | Notes |
|----------|-------|----- |
| Neo4j Integration | 18 | Testcontainers |
| PostgreSQL AGE | 18 | Testcontainers |
| PostgreSQL SQL | 18 | Testcontainers |
| Security | 49 | InputSanitizer, AuditLogger |
| Validation | 29 | JSR-380 DTOs |
| Thread Safety | 21 | Concurrent stress tests |
| Configuration | 18 | CefProperties validation |
| Resilience | 7 | Real Ollama |
| **Total** | **178+** | **All passing** |

---

## Always-On Coverage (default `mvn test` in `cef-framework`)

- **Medical benchmark harness** (`MedicalBenchmarkTest`): Generates `BENCHMARK_REPORT.md` with 4 scenarios (contraindications, behavioral risk, cascading side effects, shared doctors). Uses DuckDB + JGraphT + vLLM + Ollama embeddings.
- **Advanced medical graph patterns** (`MedicalBenchmarkTest2`): Multi-path separation/aggregation, producing `BENCHMARK_REPORT_2.md`.
- **SAP financial/supply-chain suite** (`SapBenchmarkTest`): Writes `SAP_BENCHMARK_REPORT.md` for enterprise-style multi-hop traversal.
- **Graph correctness** (`InMemoryKnowledgeGraphTest`): CRUD, traversal, concurrency sanity on the JGraphT-backed store.
- **Repository layer** (`BaseRepositoryTest`): Reactive persistence for nodes/edges/chunks.
- **Retriever integration** (`OllamaKnowledgeRetrieverIntegrationTest`): Ensures `KnowledgeRetriever.retrieve` returns ranked chunks with Ollama embeddings.

---

## Optional Integration Tests (gated by system properties)

Run these only when the required services/models are available.

| Test | Purpose | How to run |
|------|---------|------------|
| `OllamaEmbeddingIntegrationTest` | Verifies real `nomic-embed-text` embeddings (dimension 768) via Spring AI and CEF wrapper | `mvn test -Dembedding.integration=true -Dtest=OllamaEmbeddingIntegrationTest` |
| `McpToolLLMIntegrationTest` | LLM (Ollama `qwq:32b`) reads MCP schema and builds graphHints for retrieval; checks fallback behavior | `mvn test -Dollama.integration=true -Dtest=McpToolLLMIntegrationTest` |
| `VllmMcpToolIntegrationTest` / `VllmMcpCallTest` | Validates MCP tool against vLLM server for end-to-end retrieval | `mvn test -Dvllm.integration=true -Dtest=VllmMcpToolIntegrationTest` |

---

## Datasets Used in Tests

- **Medical benchmark**: 177 nodes, 455 edges, chunks across patients, doctors, conditions, medications (`medical_benchmark_data.json`). Powers both benchmark suites.
- **SAP benchmark**: Vendors, materials, invoices, projects, budgets with 4–6 hop relations (`sap_data/*.csv` parsed by `SapDataParser`).
- **Fixtures for ad-hoc scenarios**: `MedicalDomainFixtures` and `LegalDomainFixtures` create small graphs for MCP tool and retriever tests.

---

## Reproduction Recipes

```bash
cd cef-framework

# Full default suite (benchmarks + graph + repository tests)
mvn test

# Specific reports
mvn -Dtest=MedicalBenchmarkTest test
mvn -Dtest=MedicalBenchmarkTest2 test
mvn -Dtest=SapBenchmarkTest test

# Verify graph store correctness only
mvn -Dtest=InMemoryKnowledgeGraphTest test
```

Reports are emitted alongside the module (`cef-framework/BENCHMARK_REPORT*.md`, `SAP_BENCHMARK_REPORT.md`). Integration tests log the external endpoints they hit (Ollama, vLLM) so you can confirm connectivity.

---

## What to Watch

- **Latency vs. coverage:** Medical domains show 60–220% chunk lift with ~4–13 ms overhead; SAP shows parity in coverage but higher traversal cost (75–110% latency).
- **Graph size constraints:** JGraphT is validated up to ~100K nodes (see Known Issues for memory guidance). Move to Neo4j/pgvector when you exceed that.
- **Fallback discipline:** If graph traversal yields &lt; 3 chunks, retrieval falls back to vector-only; benchmarks assert this behavior so regressions are caught. 
