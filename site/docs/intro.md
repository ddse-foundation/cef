# Why CEF Instead of Naive RAG

**CEF is a knowledge-model ORM for LLMs.** It treats entities and relationships the way Hibernate treats tables and joins, then persists both graph structure and semantic chunks so retrieval can reason over relationships instead of only cosine similarity.

**Benchmarked advantage:** In the medical benchmark suite, the knowledge model retrieved **60–220% more relevant chunks** than vector-only RAG while adding only **~4 ms latency** (average 26 ms vs 21.8 ms). Advanced scenarios (3–5 hop queries) pulled **6–9 more results** than naive RAG.

---

## Why Not Vector-Only RAG

- **No structural reasoning:** Vector search cannot follow `Patient → Medication → ContraindicatedFor → Condition` chains. In Scenario 4 of the benchmark, vector-only returned 5 chunks; the knowledge model returned 16 (+220%).
- **Semantic bias, low coverage:** Queries with words like "medication" matched condition summaries, missing the actual drug profiles and patients (Scenario 1: 12 vs 5 chunks, +140%).
- **Transitive queries fail:** "Patients sharing doctors with immunocompromised CHF patients" needs relationship traversal; pure vectors have no notion of shared doctors.
- **Context waste:** Without graph hints, token budgets get spent on loosely related text instead of the connected entities required to answer the question.

---

## What CEF Adds

- 🗄️ **Knowledge ORM APIs:** `KnowledgeIndexer` (persist nodes/edges/chunks) and `KnowledgeRetriever` (pattern-aware retrieval) mirror familiar JPA-style patterns.
- 🔄 **Dual persistence:** Graph store (relationships) + vector store (semantics) stay in sync.
- 🔍 **Three-stage retrieval:** Graph traversal → hybrid graph-constrained vector search → vector fallback, so you never silently drop into "vector-only".
- 📦 **Pluggable storage:** Tested defaults (DuckDB + JGraphT). Configured adapters for PostgreSQL/pgvector, Neo4j, Qdrant, Pinecone (see Known Issues for test status).
- 🔌 **LLM tooling:** MCP tool for schema-aware prompts, OpenAI/Ollama/vLLM clients, with benchmarks executed on vLLM Qwen3-Coder-30B + Ollama nomic-embed-text.
- 📊 **Evidence-backed:** Medical suite (177 nodes, 455 edges) shows 60–220% retrieval lift; supply-chain SAP suite exercises enterprise-style relationships.

---

## Market Comparison (v0.6)

|                     | Vector-Only RAG (typical) | Graph DB + custom GraphRAG | **CEF Knowledge ORM** |
|---------------------|---------------------------|----------------------------|-----------------------|
| **Goal**            | Similarity search over chunks | Manual graph modeling plus bespoke prompt work | **Local/Embedded** ORM for knowledge models |
| **Storage**         | Vector DB only            | Graph DB only              | Graph + vector stores (Dual Persistence) |
| **Retrieval**       | Cosine similarity         | Cypher/Gremlin queries; prompt engineering | Pattern traversal → hybrid → fallback (automatic) |
| **Developer UX**    | Ad-hoc scripts, little schema | Custom ingestion/migrations per project | JPA-like APIs (`indexNode`, `retrieve`), lifecycle hooks |
| **LLM Integration** | RAG chains per app        | Hand-written prompts per graph | MCP tool with schema injection for LLM tool-calling |
| **Evidence**        | –                         | –                          | Benchmarked +120% avg chunk lift (medical) in **research scenarios** |

---

## v0.6 Facts (No Placeholders)

- **Graph Stores (5 tested):** Neo4j, PostgreSQL+AGE, PostgreSQL SQL, DuckDB, In-Memory (JGraphT)
- **Vector Stores (4 tested):** Neo4j, PostgreSQL+pgvector, DuckDB VSS, In-Memory
- **LLM stack:** vLLM Qwen3-Coder-30B for generation, Ollama nomic-embed-text (768d) for embeddings
- **Domains covered:** Medical clinical decision support (177 nodes / 455 edges) and SAP-style financial/supply-chain workflows
- **Results:** Medical benchmarks show +60–220% more relevant chunks; advanced separation/aggregation patterns add up to 9 extra chunks
- **Test coverage:** 178+ integration tests with real infrastructure via Testcontainers (no mocks)
- **New in v0.6:** Resilience patterns (retry, circuit breaker, timeout), security foundations (API-key, sanitization, audit), thread safety

---

## When to Use CEF

- You need **entity-aware retrieval** (patients, doctors, vendors, materials) with multi-hop reasoning.
- You want **storage agnosticism** (start in DuckDB/JGraphT, graduate to PostgreSQL/Neo4j without rewriting code).
- You must keep **graph structure and semantic text aligned** and reproducible for audits/benchmarks.

## When Not to Use CEF

- Pure document search with no relationships.
- One-off prototypes where a simple vector DB suffices.
- Graphs far beyond in-memory limits without moving to Neo4j (JGraphT is validated to ~100K nodes).

---

## Pick Your Next Stop

- **Run it now:** See [Quickstart](quickstart.md) for a 10-minute path with DuckDB + Ollama.
- **Understand the engine:** Read [Architecture](architecture.md) for traversal, fallback, and storage internals.
- **Build something real:** Follow the [Hands-on tutorial](tutorials/build-your-first-model.md) to define relation types, index data, and retrieve context.
- **Inspect proof:** Review [Benchmarks](benchmarks.md) and [Validation & Tests](validation.md).
