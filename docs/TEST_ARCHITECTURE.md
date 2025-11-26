# Test Architecture

## Overview

This document outlines the comprehensive testing strategy for the CEF (Context-Enhanced Framework) project, designed to meet international review standards with professional test organization, maintainability, and execution.

## Test Pyramid

```
                    ┌───────────────────┐
                    │ E2E Integration   │ (5%)  - Full stack with real LLMs
                    │  Ollama + vLLM    │
                    └───────────────────┘
                  ┌─────────────────────────┐
                  │ Integration Tests       │ (25%) - Database + Services
                  │ Testcontainers, H2      │
                  └─────────────────────────┘
              ┌───────────────────────────────────┐
              │  Component Tests                  │ (40%) - Mocked dependencies
              │  Retriever, GraphStore, MCP       │
              └───────────────────────────────────┘
          ┌───────────────────────────────────────────┐
          │    Unit Tests                             │ (30%) - Pure logic
          │    InMemoryGraph, Repositories            │
          └───────────────────────────────────────────┘
```

## Test Categories

### 1. Unit Tests (Fast, No External Dependencies)

**Target**: Pure business logic, domain models, utilities
**Duration**: < 100ms per test
**Tools**: JUnit 5, Mockito, AssertJ

Examples:
- `InMemoryKnowledgeGraphTest` - Graph algorithms without database
- `KnowledgeRetrieverTest` - Retrieval strategies with mocked GraphStore
- Domain model validation

**Naming**: `*Test.java`
**Profile**: None (default)

### 2. Component Tests (In-Memory Database)

**Target**: Repository layer, R2DBC interactions, schema validation
**Duration**: < 500ms per test
**Tools**: DuckDB in-memory, Spring Data R2DBC Test slice

Examples:
- `NodeRepositoryDuckDBTest` - CRUD operations
- `ChunkRepositoryDuckDBTest` - Embedding storage
- `EdgeRepositoryDuckDBTest` - Relationship queries

**Naming**: `*DuckDBTest.java` or `*ComponentTest.java`
**Profile**: `test` (uses DuckDB by default)

### 3. Integration Tests (Real Database)

**Target**: Full repository layer with PostgreSQL features (JSONB, pgvector)
**Duration**: 1-5s per test (Testcontainers startup)
**Tools**: Testcontainers, PostgreSQL 15

Examples:
- `NodeRepositoryPostgresTest` - JSONB operations
- `ChunkRepositoryPostgresTest` - pgvector similarity search
- `GraphStorePostgresIntegrationTest` - Complex graph traversals

**Naming**: `*PostgresTest.java` or `*IntegrationTest.java`
**Profile**: `integration` (Testcontainers)
**Execution**: `mvn test -Dspring.profiles.active=integration`

### 4. LLM Integration Tests (Real AI Services)

**Target**: End-to-end validation with real LLMs and embedding models
**Duration**: 5-30s per test (LLM inference time)
**Tools**: Ollama (qwq:32b, nomic-embed-text), vLLM (Qwen3-Coder-30B)

Examples:
- `OllamaEmbeddingIntegrationTest` - Real embeddings
- `McpToolLLMIntegrationTest` - Schema parsing with Ollama
- `VllmMcpToolIntegrationTest` - MCP tool invocation with vLLM
- `OllamaKnowledgeRetrieverIntegrationTest` - Semantic search end-to-end

**Naming**: `*LLMIntegrationTest.java`
**Profile**: `ollama-integration` or `vllm-integration`
**Execution**: 
- `mvn test -Dollama.integration=true -Dtest=*OllamaIntegrationTest`
- `mvn test -Dvllm.integration=true -Dtest=*VllmIntegrationTest`

**Prerequisites**:
```bash
# Ollama (localhost:11434)
ollama pull qwq:32b
ollama pull nomic-embed-text:latest

# vLLM (localhost:8001)
vllm serve Qwen/Qwen3-Coder-30B-A3B-Instruct-FP8 \
  --dtype auto --cpu-offload-gb 100 --gpu-memory-utilization 0.80 \
  --max-model-len 72224 --max-num-batched-tokens 72224 \
  --max-num-seqs 4 --tensor-parallel-size 1 --enforce-eager \
  --tool-call-parser hermes --enable-auto-tool-choice --port 8001
```

## Test Infrastructure

### Base Test Classes

All tests **MUST** extend appropriate base classes for consistent setup:

#### 1. `BaseUnitTest` (Abstract)
- No Spring context
- Utility methods for mock data creation
- AssertJ custom assertions

#### 2. `BaseDuckDBTest` (Abstract)
```java
@DataR2dbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
abstract class BaseDuckDBTest {
    @DynamicPropertySource
    static void configureDuckDB(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:pool:duckdb:mem:testdb");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-duckdb.sql");
    }
    
    @BeforeEach
    void cleanDatabase() { /* Clean tables */ }
}
```

#### 3. `BasePostgresIntegrationTest` (Abstract)
```java
@DataR2dbcTest
@Testcontainers
abstract class BasePostgresIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb");
    
    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) { /* ... */ }
    
    @BeforeAll
    static void initializeSchema() { /* Run schema-postgresql.sql */ }
    
    @BeforeEach
    void cleanDatabase() { /* Truncate tables */ }
}
```

#### 4. `BaseLLMIntegrationTest` (Abstract)
```java
@SpringBootTest
@EnabledIfSystemProperty(named = "ollama.integration", matches = "true")
abstract class BaseLLMIntegrationTest extends BaseDuckDBTest {
    @Autowired protected EmbeddingModel embeddingModel;
    @Autowired protected ChatClient.Builder chatClientBuilder;
    
    @BeforeAll
    static void verifyLLMServices() {
        // Verify Ollama/vLLM reachable
        // Pre-warm models
    }
    
    @BeforeEach
    void setupTestData() {
        // Create nodes, chunks with real embeddings
        // Common medical/legal domain fixtures
    }
    
    @AfterEach
    void cleanupTestData() {
        // Clean repositories
    }
}
```

### Test Fixtures and Builders

All test data creation **MUST** use builders, not manual construction:

#### `TestDataBuilder` - Fluent API
```java
Node patient = TestDataBuilder.node()
    .label("Patient")
    .property("name", "John Doe")
    .property("age", 45)
    .vectorizableContent("Patient John Doe, age 45, diabetic")
    .build();

Chunk chunk = TestDataBuilder.chunk()
    .content("Diabetes treatment guidelines")
    .linkedTo(patient.getId())
    .withRealEmbedding(embeddingModel) // Uses real LLM
    .metadata("source", "medical_journal.pdf")
    .build();
```

#### Domain Fixtures
```java
@Component
public class MedicalDomainFixtures {
    public MedicalScenario createDiabetesScenario(EmbeddingModel embeddingModel) {
        // Returns: Patient, Doctor, Condition, Medication nodes + edges + chunks
    }
}

@Component
public class LegalDomainFixtures {
    public LegalScenario createContractScenario(EmbeddingModel embeddingModel) {
        // Returns: Contract, Party, Clause, Obligation nodes + edges + chunks
    }
}
```

### Test Organization with @Nested

Every integration test **MUST** use `@Nested` for logical grouping:

```java
@SpringBootTest
class McpToolLLMIntegrationTest extends BaseLLMIntegrationTest {
    
    @Nested
    @DisplayName("Schema Understanding Tests")
    class SchemaUnderstandingTests {
        @Test void shouldParseToolSchema() { /* ... */ }
        @Test void shouldIdentifyNodeTypes() { /* ... */ }
    }
    
    @Nested
    @DisplayName("GraphHints Construction Tests")
    class GraphHintsConstructionTests {
        @Test void shouldConstructFromNaturalLanguage() { /* ... */ }
        @Test void shouldHandleAmbiguousReferences() { /* ... */ }
    }
    
    @Nested
    @DisplayName("End-to-End Retrieval Tests")
    class EndToEndRetrievalTests {
        @Test void shouldRetrieveContextWithGraphHints() { /* ... */ }
        @Test void shouldFallbackToSemanticSearch() { /* ... */ }
    }
}
```

## Lifecycle Management

### Critical Rules

1. **@BeforeAll**: Expensive one-time setup
   - Schema initialization
   - LLM service verification
   - Static test data loading

2. **@BeforeEach**: Per-test isolation
   - Database cleanup/reset
   - Test-specific fixtures
   - Reset mocks

3. **@AfterEach**: Cleanup
   - Truncate test data
   - Release resources
   - Verify no side effects

4. **@AfterAll**: Teardown
   - Close Testcontainers
   - Shutdown LLM clients
   - Report metrics

### Anti-Patterns to Avoid

❌ **NO** manual `deleteAll()` calls in tests
❌ **NO** static flags like `schemaInitialized`
❌ **NO** test-order dependencies
❌ **NO** shared mutable state between tests
❌ **NO** inline test data creation (use builders)
❌ **NO** `Thread.sleep()` for async operations (use `StepVerifier`)

## Test Profiles

| Profile                  | Database | LLM      | Use Case                        |
|--------------------------|----------|----------|---------------------------------|
| `test` (default)         | DuckDB   | Mocked   | Fast unit/component tests       |
| `integration`            | Postgres | Mocked   | Database integration tests      |
| `ollama-integration`     | DuckDB   | Ollama   | LLM tests with Ollama           |
| `vllm-integration`       | DuckDB   | vLLM     | LLM tests with vLLM             |
| `embedding-integration`  | DuckDB   | Ollama   | Just embedding tests            |

## Execution Commands

```bash
# Fast unit + component tests (DuckDB in-memory)
mvn clean test

# Full integration tests (Testcontainers PostgreSQL)
mvn verify -Dspring.profiles.active=integration

# Ollama LLM integration tests
mvn test -Dollama.integration=true -Dtest=*OllamaIntegrationTest

# vLLM integration tests
mvn test -Dvllm.integration=true -Dtest=*VllmIntegrationTest

# Embedding-only tests
mvn test -Dembedding.integration=true -Dtest=*EmbeddingIntegrationTest

# Specific test class
mvn test -Dtest=McpToolLLMIntegrationTest

# Specific test method
mvn test -Dtest=McpToolLLMIntegrationTest#shouldConstructGraphHintsFromNaturalLanguageQuery_Ollama

# With coverage report
mvn clean test jacoco:report
open target/site/jacoco/index.html

# Parallel execution (4 threads)
mvn test -T 4

# Skip integration tests
mvn test -DskipITs

# Debug mode
mvn test -Dmaven.surefire.debug
```

## Coverage Requirements

| Layer                | Minimum Coverage |
|----------------------|------------------|
| Domain Models        | 90%              |
| Repositories         | 85%              |
| Services/Retrievers  | 80%              |
| Graph Algorithms     | 90%              |
| MCP Tools            | 70%              |
| Configuration        | 50%              |

## Continuous Integration

GitHub Actions workflow runs:
1. **PR validation**: Fast tests (DuckDB) only
2. **Main branch**: All tests including Postgres integration
3. **Nightly**: LLM integration tests (if services available)

## Real-World LLM MCP Integration Test Scenarios

### Scenario 1: Medical Diagnosis Chain with Graph Traversal

**Objective**: Prove LLM constructs graph hints from natural language and retrieves structured context

**Setup** (in `@BeforeEach`):
```java
// Create medical knowledge graph
Patient patient = TestDataBuilder.node()
    .label("Patient")
    .property("patientId", "P12345")
    .property("name", "John Smith")
    .property("age", 67)
    .vectorizableContent("Patient John Smith, 67 years old, diabetic with hypertension")
    .build();

Doctor doctor = TestDataBuilder.node()
    .label("Doctor")
    .property("doctorId", "D001")
    .property("name", "Dr. Sarah Johnson")
    .property("specialty", "Endocrinology")
    .build();

Condition diabetes = TestDataBuilder.node()
    .label("Condition")
    .property("conditionId", "C001")
    .property("name", "Type 2 Diabetes")
    .property("icd10", "E11")
    .vectorizableContent("Type 2 diabetes mellitus with insulin resistance")
    .build();

Condition hypertension = TestDataBuilder.node()
    .label("Condition")
    .property("conditionId", "C002")
    .property("name", "Hypertension")
    .property("icd10", "I10")
    .build();

Medication metformin = TestDataBuilder.node()
    .label("Medication")
    .property("medicationId", "M001")
    .property("name", "Metformin")
    .property("dosage", "500mg twice daily")
    .build();

Medication lisinopril = TestDataBuilder.node()
    .label("Medication")
    .property("medicationId", "M002")
    .property("name", "Lisinopril")
    .property("dosage", "10mg daily")
    .build();

// Create relationship edges
Edge treatedBy = createEdge(patient.getId(), doctor.getId(), "TREATED_BY");
Edge hasDiabetes = createEdge(patient.getId(), diabetes.getId(), "HAS_CONDITION");
Edge hasHypertension = createEdge(patient.getId(), hypertension.getId(), "HAS_CONDITION");
Edge prescribedMetformin = createEdge(diabetes.getId(), metformin.getId(), "PRESCRIBED_MEDICATION");
Edge prescribedLisinopril = createEdge(hypertension.getId(), lisinopril.getId(), "PRESCRIBED_MEDICATION");

// Create semantic chunks with real Ollama embeddings
Chunk diabetesInfo = TestDataBuilder.chunk()
    .content("Type 2 diabetes management requires blood glucose monitoring, dietary modifications, " +
             "and medication like Metformin to improve insulin sensitivity.")
    .linkedTo(diabetes.getId())
    .withRealEmbedding(embeddingModel)
    .metadata("source", "clinical_guidelines.pdf")
    .build();

Chunk hypertensionInfo = TestDataBuilder.chunk()
    .content("Hypertension in diabetic patients increases cardiovascular risk. " +
             "ACE inhibitors like Lisinopril are first-line treatment.")
    .linkedTo(hypertension.getId())
    .withRealEmbedding(embeddingModel)
    .build();

// Save all to DuckDB
saveAll(patient, doctor, diabetes, hypertension, metformin, lisinopril);
saveAll(treatedBy, hasDiabetes, hasHypertension, prescribedMetformin, prescribedLisinopril);
saveAll(diabetesInfo, hypertensionInfo);
```

**Test Case 1: LLM Constructs Graph Hints from Natural Language**
```java
@Test
@DisplayName("LLM should parse natural query and construct graphHints for traversal")
void shouldConstructGraphHintsFromNaturalLanguage() {
    // Given - Natural language query
    String userQuery = "What medications is patient P12345 taking for diabetes?";
    
    // When - LLM MCP tool invoked (Ollama qwq:32b)
    McpToolRequest request = McpToolRequest.builder()
        .query(userQuery)
        .build();
    
    // Expected LLM reasoning:
    // 1. Identify "P12345" as patientId property → startNodeLabel: Patient
    // 2. Understand "medications" → targetNodeLabel: Medication
    // 3. Parse "for diabetes" → intermediate: Condition (Type 2 Diabetes)
    // 4. Construct path: Patient -[HAS_CONDITION]-> Condition -[PRESCRIBED_MEDICATION]-> Medication
    
    StepVerifier.create(mcpTool.invoke(request))
        .assertNext(context -> {
            // Verify LLM used GRAPH retrieval, not just semantic fallback
            assertThat(context.getMetadata()).containsEntry("strategy", "GRAPH_TRAVERSAL");
            
            // Verify graphHints constructed by LLM
            GraphHints hints = context.getGraphHints();
            assertThat(hints.getStartNodeLabels()).contains("Patient");
            assertThat(hints.getStartNodeProperties())
                .containsEntry("patientId", "P12345");
            assertThat(hints.getRelationTypes())
                .containsExactlyInAnyOrder("HAS_CONDITION", "PRESCRIBED_MEDICATION");
            assertThat(hints.getTraversalDepth()).isEqualTo(2);
            
            // Verify correct data retrieved via graph traversal
            assertThat(context.getContent()).contains("Metformin");
            assertThat(context.getContent()).contains("500mg twice daily");
            assertThat(context.getContent()).contains("Type 2 Diabetes");
            
            // Should NOT contain hypertension medication (not in query scope)
            assertThat(context.getContent()).doesNotContain("Lisinopril");
            
            // Print empirical proof
            logger.info("=== LLM Graph Query Construction ===");
            logger.info("User Query: {}", userQuery);
            logger.info("LLM Identified Start Node: Patient with patientId=P12345");
            logger.info("LLM Constructed Path: Patient -> HAS_CONDITION -> Condition -> PRESCRIBED_MEDICATION -> Medication");
            logger.info("Retrieved Nodes: {}", context.getNodes().size());
            logger.info("Retrieved Edges: {}", context.getEdges().size());
            logger.info("Context: {}", context.getContent());
        })
        .verifyComplete();
}
```

**Test Case 2: Multi-Node Identification with Complex Traversal**
```java
@Test
@DisplayName("LLM should identify 3 nodes and construct complex multi-hop traversal path")
void shouldHandleComplexThreeNodeTraversal() {
    // Given - Query requiring 3-node path: Patient -> Doctor -> Condition
    String userQuery = "What conditions does Dr. Sarah Johnson's patient John Smith have?";
    
    // When - LLM must identify:
    // 1. "John Smith" → Patient (by name property)
    // 2. "Dr. Sarah Johnson" → Doctor (by name property)  
    // 3. "conditions" → Condition (target node type)
    // Path: Patient -[TREATED_BY]-> Doctor (verify relationship) then Patient -[HAS_CONDITION]-> Condition
    
    McpToolRequest request = McpToolRequest.builder()
        .query(userQuery)
        .build();
    
    StepVerifier.create(mcpTool.invoke(request))
        .assertNext(context -> {
            // Verify LLM identified multiple starting points
            GraphHints hints = context.getGraphHints();
            assertThat(hints.getStartNodeLabels())
                .containsExactlyInAnyOrder("Patient", "Doctor");
            assertThat(hints.getStartNodeProperties())
                .containsEntry("name", "John Smith")
                .containsEntry("name", "Dr. Sarah Johnson");
            
            // Verify path includes relationship validation
            assertThat(hints.getRelationTypes())
                .contains("TREATED_BY", "HAS_CONDITION");
            
            // Verify both conditions retrieved
            assertThat(context.getContent())
                .contains("Type 2 Diabetes")
                .contains("Hypertension")
                .contains("E11") // ICD-10 code
                .contains("I10");
            
            // Verify doctor-patient relationship validated
            assertThat(context.getEdges())
                .extracting(Edge::getRelationType)
                .contains("TREATED_BY");
            
            logger.info("=== Complex 3-Node LLM Traversal ===");
            logger.info("Query: {}", userQuery);
            logger.info("LLM Identified: Patient(John Smith) + Doctor(Dr. Sarah Johnson) + Target(Condition)");
            logger.info("Traversal Path: Patient -[TREATED_BY]-> Doctor (validated) && Patient -[HAS_CONDITION]-> Condition");
            logger.info("Conditions Found: {}", context.getNodes().stream()
                .filter(n -> n.getLabel().equals("Condition"))
                .map(n -> n.getProperties().get("name"))
                .toList());
        })
        .verifyComplete();
}
```

**Test Case 3: Fallback to Semantic Search When Graph Hints Insufficient**
```java
@Test
@DisplayName("LLM should fallback to semantic search when graph query yields no results")
void shouldFallbackToSemanticWhenGraphQueryEmpty() {
    // Given - Ambiguous query that doesn't map cleanly to graph structure
    String userQuery = "Tell me about managing blood sugar in elderly patients";
    
    // When - LLM tries graph hints but:
    // 1. "elderly patients" is too vague for specific node identification
    // 2. "managing blood sugar" doesn't map to single relationship type
    // 3. Graph traversal returns 0 results
    // → System should AUTO-FALLBACK to semantic vector search
    
    McpToolRequest request = McpToolRequest.builder()
        .query(userQuery)
        .fallbackToSemantic(true) // Enable automatic fallback
        .build();
    
    StepVerifier.create(mcpTool.invoke(request))
        .assertNext(context -> {
            // Verify fallback occurred
            assertThat(context.getMetadata())
                .containsEntry("strategy", "SEMANTIC_FALLBACK")
                .containsEntry("graphQueryAttempted", "true")
                .containsEntry("graphResultsCount", "0");
            
            // Verify semantic search found relevant chunks
            assertThat(context.getChunks()).isNotEmpty();
            assertThat(context.getContent())
                .containsIgnoringCase("diabetes")
                .containsIgnoringCase("blood glucose")
                .containsAnyOf("Metformin", "insulin", "monitoring");
            
            // Should include linked nodes from chunks
            assertThat(context.getNodes()).isNotEmpty();
            
            logger.info("=== LLM Fallback Mechanism ===");
            logger.info("Query: {}", userQuery);
            logger.info("LLM Initial Attempt: Graph query with vague hints");
            logger.info("Graph Results: 0 nodes/edges");
            logger.info("Fallback Triggered: Semantic search using embeddings");
            logger.info("Semantic Results: {} chunks, {} nodes", 
                context.getChunks().size(), context.getNodes().size());
            logger.info("Top Result: {}", context.getChunks().get(0).getContent().substring(0, 100));
        })
        .verifyComplete();
}
```

**Test Case 4: Hybrid Strategy - Graph + Semantic Enrichment**
```java
@Test
@DisplayName("LLM should combine graph traversal with semantic expansion for comprehensive results")
void shouldUseHybridStrategyForRichContext() {
    // Given - Query benefiting from BOTH graph structure + semantic content
    String userQuery = "Explain the treatment plan for patient P12345";
    
    // When - LLM uses hybrid strategy:
    // 1. Graph traversal: Patient -> Conditions -> Medications (structured data)
    // 2. Semantic search: "treatment plan" (clinical guidelines, explanations)
    // 3. Merge results for comprehensive context
    
    McpToolRequest request = McpToolRequest.builder()
        .query(userQuery)
        .strategy(RetrievalStrategy.HYBRID)
        .graphDepth(2)
        .semanticTopK(3)
        .build();
    
    StepVerifier.create(mcpTool.invoke(request))
        .assertNext(context -> {
            // Verify hybrid strategy used
            assertThat(context.getMetadata())
                .containsEntry("strategy", "HYBRID");
            
            // Verify structured graph data present
            assertThat(context.getNodes())
                .extracting(Node::getLabel)
                .contains("Patient", "Condition", "Medication");
            assertThat(context.getEdges()).isNotEmpty();
            
            // Verify semantic chunks provide explanatory content
            assertThat(context.getChunks()).isNotEmpty();
            String fullContent = context.getContent();
            assertThat(fullContent)
                .contains("P12345") // Structured data
                .contains("Type 2 Diabetes") // Graph data
                .contains("Metformin") // Graph data
                .containsAnyOf("management", "treatment", "monitoring"); // Semantic content
            
            // Verify chunks are semantically relevant (not just linked)
            List<Float> similarities = context.getChunks().stream()
                .map(Chunk::getEmbedding)
                .map(emb -> cosineSimilarity(emb, embeddingModel.embed(userQuery)))
                .toList();
            assertThat(similarities).allMatch(sim -> sim > 0.6); // High semantic relevance
            
            logger.info("=== LLM Hybrid Strategy ===");
            logger.info("Query: {}", userQuery);
            logger.info("Graph Component: {} nodes, {} edges", 
                context.getNodes().size(), context.getEdges().size());
            logger.info("Semantic Component: {} chunks", context.getChunks().size());
            logger.info("Avg Semantic Similarity: {}", 
                similarities.stream().mapToDouble(Float::doubleValue).average().orElse(0.0));
            logger.info("Combined Context Length: {} chars", fullContent.length());
        })
        .verifyComplete();
}
```

### Scenario 2: Legal Contract Analysis with Entity Resolution

**Setup** (Legal domain):
```java
// Contract nodes with ambiguous references
Contract contract1 = node("Contract", "contractId", "C-2024-001", "title", "Software License Agreement");
Contract contract2 = node("Contract", "contractId", "C-2024-002", "title", "Consulting Services Agreement");

Party acmeCorp = node("Party", "partyId", "ACME-001", "name", "ACME Corporation", "role", "Licensor");
Party techCo = node("Party", "partyId", "TECH-001", "name", "TechCo Inc", "role", "Licensee");
Party consultant = node("Party", "partyId", "CONS-001", "name", "John Doe", "role", "Consultant");

Clause paymentClause = node("Clause", "clauseId", "CL-001", "title", "Payment Terms", 
    "content", "Licensee shall pay $50,000 annually");

// Ambiguous query: "C-2024-001" could be contractId or mentioned in content
```

**Test Case**: LLM must resolve ambiguous entity reference and query multiple relationship types:
```java
@Test
void shouldResolveAmbiguousEntityAndTraverseMultipleRelationships() {
    String query = "Who are the parties in contract C-2024-001 and what are their payment obligations?";
    
    // LLM must:
    // 1. Identify "C-2024-001" as contractId property (not semantic match)
    // 2. Recognize need for 2 relationship types: PARTY_TO and HAS_CLAUSE
    // 3. Filter clauses by "payment" semantic match
    // 4. Construct: Contract -[PARTY_TO]-> Party AND Contract -[HAS_CLAUSE]-> Clause(payment)
    
    // Expected graphHints:
    // startNodeLabels: [Contract]
    // startNodeProperties: {contractId: "C-2024-001"}
    // relationTypes: [PARTY_TO, HAS_CLAUSE]
    // targetNodeLabels: [Party, Clause]
    // traversalDepth: 2
    // semanticFilter: "payment"
    
    // Verify LLM correctly identified both ACME Corp (Licensor) and TechCo (Licensee)
    // Verify payment clause retrieved with "$50,000 annually"
    // Verify consultant NOT included (different contract)
}
```

### Key Success Criteria

1. **Graph Query Proof**: LLM metadata shows `"strategy": "GRAPH_TRAVERSAL"` with non-empty graphHints
2. **Fallback Proof**: When graph yields 0 results, metadata shows `"semanticFallbackTriggered": true`
3. **Multi-Node Proof**: GraphHints contains 2-3 entries in `startNodeLabels` or `startNodeProperties`
4. **Performance**: Graph queries < 100ms, Semantic fallback < 500ms, Hybrid < 800ms
5. **Accuracy**: Retrieved context contains ALL expected entities, NO irrelevant data

### Empirical Logging

Each test prints:
```
=== LLM MCP Tool Invocation ===
Query: [user natural language]
LLM Schema Analysis: [how LLM parsed tool schema]
GraphHints Constructed: [JSON of hints]
Retrieval Strategy: [GRAPH/SEMANTIC/HYBRID]
Execution Time: [ms]
Results: [X nodes, Y edges, Z chunks]
Context Preview: [first 200 chars]
```

This proves to reviewers that LLM truly understands MCP tool schema and constructs structured queries, not just doing blind semantic search.

## Future Enhancements

1. **Contract Testing**: MCP tool schema contracts
2. **Performance Tests**: JMH benchmarks for graph algorithms
3. **Mutation Testing**: PIT for code quality
4. **Chaos Engineering**: Random failures in integration tests
5. **Property-Based Testing**: QuickTheories for edge cases

## Review Checklist

Before committing tests, ensure:
- ✅ Extends appropriate base test class
- ✅ Uses test builders, not manual construction
- ✅ Organized with `@Nested` classes
- ✅ Proper lifecycle hooks (`@BeforeEach`, etc.)
- ✅ No test-order dependencies
- ✅ Descriptive test names (`should...When...`)
- ✅ Javadoc explaining test purpose
- ✅ Assertions use AssertJ fluent API
- ✅ Async tests use `StepVerifier`
- ✅ No `System.out.println()` (use logger)
- ✅ Passes `mvn clean test`
