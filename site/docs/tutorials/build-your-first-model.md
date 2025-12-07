# Hands-On: Build a Knowledge Model in 30 Minutes

This tutorial walks through the same patterns exercised by the medical benchmark: defining relation semantics, indexing nodes/edges/chunks, and retrieving multi-hop context. Everything below is grounded in the v0.6 code and tests (no placeholders).

---

## 1) Add the Framework

```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>0.6</version>
</dependency>
```

---

## 2) Configure a Tested Stack (DuckDB + Ollama)

`src/main/resources/application.yml`

```yaml
cef:
  graph:
    store: duckdb            # Default - or: in-memory, neo4j, pg-sql, pg-age
  vector:
    store: duckdb            # Default - or: in-memory, neo4j, postgresql
  llm:
    default-provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: nomic-embed-text

spring:
  main:
    web-application-type: reactive
```

> v0.6 supports 5 graph backends and 4 vector backends. The default (DuckDB) requires no external services. vLLM (Qwen3-Coder-30B) was used for benchmark generation; you can plug it in later without changing code.

---

## 3) Declare Relation Semantics (Like JPA Mappings)

```java
@Configuration
public class KnowledgeModelConfig {

    private final KnowledgeIndexer indexer;

    public KnowledgeModelConfig(KnowledgeIndexer indexer) {
        this.indexer = indexer;
    }

    @PostConstruct
    public void initializeRelations() {
        var relationTypes = List.of(
            new RelationType("TREATS", RelationSemantics.CAUSAL, true,
                "Doctor treats patient"),
            new RelationType("HAS_CONDITION", RelationSemantics.ASSOCIATIVE, false,
                "Patient has medical condition"),
            new RelationType("PRESCRIBED_MEDICATION", RelationSemantics.CAUSAL, false,
                "Patient prescribed medication")
        );

        indexer.initialize(relationTypes).block();
    }
}
```

These semantics mirror the benchmark scenarios (contraindications, comorbidities, shared doctors).

---

## 4) Index Nodes, Edges, and Chunks (Dual Persistence)

```java
// Entity nodes (graph + optional vectorizable content)
Node patient = new Node(
    null, "Patient",
    Map.of("name", "John Doe", "age", 45, "gender", "M"),
    "45-year-old male with type 2 diabetes and hypertension."
);
Node condition = new Node(
    null, "Condition",
    Map.of("name", "Type 2 Diabetes", "icd10", "E11.9"),
    "**CONDITION PROFILE** Name: Type 2 Diabetes Mellitus..."
);
UUID patientId = indexer.indexNode(patient).block().getId();
UUID conditionId = indexer.indexNode(condition).block().getId();

// Typed relationship
Edge hasCondition = new Edge(
    null, "HAS_CONDITION", patientId, conditionId,
    Map.of("diagnosedOn", "2025-01-10"), 1.0
);
indexer.indexEdge(hasCondition).block();

// Additional chunks tied to the patient (semantic side)
Chunk encounter = new Chunk();
encounter.setContent("**CLINICAL ENCOUNTER NOTE** Patient presents with chest pain...");
encounter.setLinkedNodeId(patientId);
encounter.setMetadata(Map.of("source", "ehr", "encounterId", "ENC-1001"));
indexer.indexChunk(encounter).block();
```

All writes update both the graph store and vector store automatically (dual persistence).

---

## 5) Retrieve Multi-Hop Context (Same Flow as Benchmarks)

```java
public Mono<RetrievalResult> findPatientContext(String patientName) {
    var graphQuery = new GraphQuery(
        List.of(new ResolutionTarget(
            patientName,          // semantic text used for entry-point resolution
            "Patient",            // type hint
            null                  // property filter
        )),
        new TraversalHint(
            3,                    // max depth
            List.of("HAS_CONDITION", "PRESCRIBED_MEDICATION"),
            null                  // both directions
        )
    );

    var request = RetrievalRequest.builder()
        .query("Find context for " + patientName)
        .graphQuery(graphQuery)
        .topK(10)
        .maxGraphNodes(50)
        .maxTokenBudget(4000)
        .build();

    return retriever.retrieve(request);
}
```

`retriever` is the `org.ddse.ml.cef.api.KnowledgeRetriever` bean provided by Spring.

Retrieval order (matches the benchmark harness):
1. Resolve candidate Patient nodes via semantic search (with the type hint).
2. Traverse `HAS_CONDITION` and `PRESCRIBED_MEDICATION` up to depth 3.
3. Run vector search constrained to the traversed subgraph.
4. Fallback to vector-only if fewer than 3 results remain.

This is the same flow that delivered 12 vs 5 chunks for contraindication discovery.

---

## 6) Validate with the Built-In Benchmarks

Run the same suite that produced the published numbers:

```bash
cd cef-framework
mvn -Dtest=MedicalBenchmarkTest test
# Reports: cef-framework/BENCHMARK_REPORT.md, BENCHMARK_REPORT_2.md
```

Key expected outputs:
- Scenario 1 (contraindications): **12 vs 5** chunks (+140%).
- Scenario 4 (shared doctors): **16 vs 5** chunks (+220%).
- Advanced separation/aggregation (Benchmark 2): **+6 to +9 chunks** over vector-only.

---

## Where to Go Next

- Swap storage backends (PostgreSQL/pgvector, Neo4j) in `application.yml` once you need larger graphs.
- Expose the MCP tool to your LLM so it receives the schema and required fields automatically.
- Add more relation types (TEMPORAL, HIERARCHY) to reflect your domain and guide traversal. 
