# Context Engineering Framework (CEF) - Requirements

**Project:** Context Engineering Framework (CEF)  
**Target Audience:** Java Bangladesh (JUGBD) Presentation  
**Date:** November 24, 2025  
**Version:** 2.0

---

## 1. Executive Summary

**Context Engineering Framework (CEF)** is a database-agnostic ORM for LLM context engineering, analogous to how Hibernate abstracts RDBMS operations. CEF abstracts knowledge graph storage and retrieval, enabling developers to build reliable, testable, and auditable LLM applications.

### Key Differentiators
- **Domain Agnostic**: No built-in schemas - users define their own nodes, relationships, and chunks
- **Database Agnostic**: Switch between PostgreSQL, DuckDB, Neo4j, or Qdrant without code changes
- **Provider Agnostic**: Swap OpenAI, Ollama, vLLM seamlessly
- **Fast**: JGraphT in-memory graph with O(1) lookups and dual persistence
- **Production Ready**: Built for real work - testing, auditing, and reliability included

### Project Structure
```
cef/
├── orm-framework/       # Core framework (domain agnostic)
└── example/            # Medical domain demo with React UI
    ├── backend/        # Spring Boot + CEF framework
    └── frontend/       # React chat + graph visualization
```

---

## 2. Project Objectives

### Must Have
1. **Two Simple Interfaces**: `KnowledgeIndexer` and `KnowledgeRetriever` (MCP tool)
2. **Pluggable GraphStore**: JGraphT (default), Neo4j, TinkerPop (optional) - developer choice
3. **Pluggable VectorStore**: Postgres/pgvector (default), DuckDB, Qdrant, Pinecone (optional)
4. **Intelligent Search**: Hybrid (graph+vector) → VSS → BM25 automatic fallback
5. **Parser System**: Simple formats (YAML/CSV/JSON), ANTLR optional for complex PDFs
6. **DataSource Adapters**: Filesystem, S3, MinIO
6. **LLM Client Factory**: OpenAI, Ollama, vLLM, Mock
7. **Context Provider Tool**: Multi-query assembly with token budget management
8. **Medical Example**: 50 patients, 10 doctors, 30 conditions + React UI with graph visualization
9. **Entity Lifecycle Hooks**: Pre/post insert, update, delete callbacks
10. **Caching Layer**: First-level (session) and second-level (shared) cache for retrieval optimization
11. **Query DSL**: Fluent query builder for complex graph queries
12. **Schema Migration**: Automated versioning and migration (Flyway/Liquibase integration)
13. **Validation Framework**: Bean Validation (JSR-303) integration
14. **Observability**: Metrics, logging, query profiling, slow query detection
15. **Connection Pooling**: HikariCP integration with health checks
16. **Batch Indexing**: Optimized bulk loading for initial/periodic heavy indexing

### Nice to Have
- Neo4j adapter (prove multi-DB support)
- Document chunking strategies (semantic, sliding window)
- Graph visualization export (PNG/SVG)
- Query performance profiler
- Distributed caching (Redis/Hazelcast)
- Multi-tenancy support
- Soft delete support
- Audit logging (who changed what, when)

---

## 3. Architecture Overview

See **ADR-002** for detailed technical architecture.

### Core Philosophy
**"Bulk indexing (heavy), frequent retrieval (optimized)"**
- Indexing happens infrequently but can be heavy (thousands of nodes in single transaction)
- Retrieval happens constantly and must be fast (<100ms)
- Optimize caching, in-memory graph, and query paths for retrieval performance
- Accept indexing latency for retrieval speed

### Core Principles
1. **Framework doesn't know domains**: No "Patient" or "Doctor" - only `Node`, `Edge`, `Chunk`
2. **User defines semantics**: Map domain relations to `RelationSemantics` (HIERARCHY, CLASSIFICATION, ASSOCIATION, etc.)
3. **Graph reasoning enhances VSS**: Extract paths/keywords from graph to improve vector search
4. **Dual persistence**: Every write updates both database and in-memory graph
5. **Industry-standard ORM features**: Lifecycle hooks, caching, validation, migrations
6. **Trust LLM intelligence**: With proper schema + examples, LLMs construct correct queries

### Components (Framework Provides)
- **Knowledge Model**: Node, Edge, RelationType, Chunk, RelationSemantics (enum)
- **Indexing**: `fullIndex()`, `indexNode()`, `indexEdge()`, `indexChunk()`
- **Retrieval**: `retrieve()` (MCP tool), `extractContext()`, `getParents()`, `getChildren()`, `getSiblings()`
- **Parser System**: AbstractParser, ParserFactory, built-in YAML/CSV/JSON parsers
- **DataSource**: FileSystemDataSource, BlobStorageDataSource (S3/MinIO)
- **LLM Clients**: OpenAI, Ollama, vLLM, Mock
- **Context Provider**: Multi-query assembly with token budget
- **Lifecycle Management**: EntityListener, PrePersist, PostPersist, PreUpdate, PostUpdate, PreRemove, PostRemove
- **Caching**: L1 (session), L2 (shared), query result cache
- **Query DSL**: Type-safe fluent query API
- **Migrations**: Schema versioning and automated migration
- **Validation**: JSR-303 Bean Validation integration
- **Observability**: Metrics (Micrometer), distributed tracing (OpenTelemetry), slow query logging

---

## 4. Core Framework Features

### 4.1 Knowledge Indexing
```java
// Initialize with root nodes and relation types (user's responsibility)
indexer.initialize(rootNodes, List.of(
    new RelationType("IS_A", RelationSemantics.CLASSIFICATION),
    new RelationType("HAS_SYMPTOM", RelationSemantics.ASSOCIATION),
    new RelationType("PART_OF", RelationSemantics.HIERARCHY)
));

// Full index from data source
indexer.fullIndex(new FileSystemDataSource("data/seed"));

// Incremental indexing with validation
Node patient = indexer.indexNode(new NodeInput(
    null, "Patient", Map.of("name", "John", "age", 45),
    "Patient has chest pain and shortness of breath"  // Auto-vectorized
));
```

### 4.2 Intelligent Retrieval (3-Level Fallback)
```java
SearchResult result = retriever.retrieve(
    RetrievalRequest.builder()
        .startNodeId(patientId)       // Graph reasoning start point
        .query("treatment options")
        .depth(2)                      // Traverse 2 hops
        .minResults(3)                 // Fallback threshold
        .topK(10)
        .build()
);

// Algorithm:
// 1. Hybrid: Graph traversal + VSS constrained to related nodes
// 2. If results < 3 → Pure VSS (all chunks)
// 3. If results < 3 → BM25 full-text search
```

### 4.3 Context Provider (Multi-Query + Token Budget)
```java
ContextResponse context = contextProvider.provideContext(
    ContextRequest.builder()
        .addQuery("What are diabetes symptoms?")
        .addQuery("How to treat diabetes?", diabetesNodeId, 2)
        .addQuery("Diabetes complications")
        .tokenBudget(4000)            // Stay within LLM context window
        .topKPerQuery(10)
        .build()
);

String promptContext = context.toPromptContext();  // Ready for LLM
```

### 4.4 Parser System (User Extensible)

**Simple Formats** - Use standard libraries (SnakeYAML, Jackson, OpenCSV):
```java
@Component
public class MedicalYamlParser extends AbstractParser<MedicalParsedData> {
    @Override public int getOrder() { return 10; }  // Lower = higher priority
    
    @Override public List<NodeInput> extractNodes(MedicalParsedData data) {
        // Custom medical domain logic using SnakeYAML
    }
}
```

**Complex Documents** - ANTLR for structured 10-50 page PDFs:
```java
@Component
public class MedicalPdfParser extends AbstractParser<LabReportData> {
    @Override public LabReportData parse(InputStream input, ParseContext context) {
        // Step 1: Extract text from PDF
        String pdfText = extractTextFromPdf(input);
        
        // Step 2: Parse with ANTLR grammar (LabReport.g4)
        LabReportLexer lexer = new LabReportLexer(CharStreams.fromString(pdfText));
        LabReportParser parser = new LabReportParser(new CommonTokenStream(lexer));
        
        // Step 3: Visit parse tree
        return new LabReportVisitor().visit(parser.labReport());
    }
}
```

### 4.5 Pluggable Storage (NEW)
```yaml
# Developer chooses storage backends
cef:
  graph:
    store: jgrapht  # jgrapht (default) | neo4j | tinkerpop
    jgrapht:
      max-nodes: 100000  # In-memory limit
    neo4j:
      uri: bolt://localhost:7687  # For millions of nodes
  
  vector:
    store: postgres  # postgres (default) | duckdb | qdrant | pinecone
    duckdb:
      path: ./data/knowledge.duckdb  # Lightweight
    qdrant:
      host: localhost  # Specialized vector DB
```

### 4.6 Entity Lifecycle Hooks (NEW)
```java
// User can define lifecycle callbacks
@EntityListener
public class PatientListener {
    
    @PrePersist
    public void beforeCreate(Node node) {
        // Validate, set defaults, audit
        node.getProperties().putIfAbsent("createdBy", getCurrentUser());
        logger.info("Creating patient: {}", node.getLabel());
    }
    
    @PostPersist
    public void afterCreate(Node node) {
        // Send notifications, update search index
        notificationService.sendCreatedEvent(node);
    }
    
    @PreUpdate
    public void beforeUpdate(Node node, Map<String, Object> changes) {
        // Audit trail, validation
        auditService.log(node.getId(), changes);
    }
    
    @PostUpdate
    public void afterUpdate(Node node) {
        // Invalidate cache, update dependent data
        cacheManager.evict("patients", node.getId());
    }
    
    @PreRemove
    public void beforeDelete(Node node) {
        // Check constraints, backup data
        if (hasActiveAppointments(node)) {
            throw new ConstraintViolationException("Cannot delete patient with active appointments");
        }
    }
    
    @PostRemove
    public void afterDelete(Node node) {
        // Cleanup related data, send events
        cleanupService.removeOrphanedData(node.getId());
    }
}

// Register listener
@Configuration
public class ListenerConfig {
    @Bean
    public PatientListener patientListener() {
        return new PatientListener();
    }
}
```

### 4.6 Caching Strategy (NEW)
```java
// First-level cache (session/request scope)
@Cacheable(value = "nodes", key = "#id")
public Optional<Node> findNode(UUID id) {
    return memoryGraph.findNode(id);
}

// Second-level cache (shared across sessions)
@Cacheable(value = "node-by-label", key = "#label")
public List<Node> findNodesByLabel(String label) {
    return memoryGraph.findNodesByLabel(label);
}

// Query result cache
@Cacheable(value = "search-results", key = "#request.hashCode()")
public SearchResult retrieve(RetrievalRequest request) {
    // ... intelligent search
}

// Cache eviction on updates
@CacheEvict(value = {"nodes", "node-by-label"}, key = "#nodeId")
public Node updateNode(UUID nodeId, Map<String, Object> properties) {
    // ... update logic
}
```

### 4.7 Batch Indexing (NEW)
```java
// Optimized bulk indexing for initial load or periodic refresh
BatchIndexResult result = indexer.indexBatch(
    BatchInput.builder()
        .nodes(nodeInputs)        // 1000s of nodes
        .edges(edgeInputs)        // 1000s of edges
        .chunks(chunkInputs)      // 1000s of chunks
        .parallelism(4)           // Parallel processing
        .batchSize(500)           // DB batch size
        .validateBeforeCommit(true)
        .build()
);

// Framework optimizations:
// - Batch DB inserts (reduces round-trips)
// - Parallel embedding generation
// - Bulk in-memory graph updates
// - Transaction management
// - Progress tracking
```

### 4.8 Query DSL (NEW)
```java
// Type-safe fluent query API
List<Node> patients = queryBuilder
    .from("Patient")
    .where("age").greaterThan(40)
    .and("status").equals("active")
    .traverse("HAS_CONDITION")
        .where("severity").equals("high")
    .depth(2)
    .orderBy("name").ascending()
    .limit(10)
    .execute();

// Complex graph queries
SearchResult result = queryBuilder
    .startFrom(patientId)
    .traverse("HAS_CONDITION", "TREATS", "PRESCRIBED_MEDICATION")
    .withSemantics(RelationSemantics.ASSOCIATION)
    .depth(3)
    .withVectorSearch("treatment options")
    .topK(5)
    .execute();
```

### 4.9 Schema Migration (NEW)
```yaml
# migrations/V1__initial_schema.sql
CREATE SCHEMA IF NOT EXISTS cef_graph;
CREATE TABLE cef_graph.nodes (...);
CREATE INDEX idx_nodes_label ON cef_graph.nodes(label);

# migrations/V2__add_audit_fields.sql
ALTER TABLE cef_graph.nodes ADD COLUMN created_by VARCHAR(255);
ALTER TABLE cef_graph.nodes ADD COLUMN updated_by VARCHAR(255);

# Java API
@Configuration
public class MigrationConfig {
    @Bean
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
    }
}
```

### 4.10 Validation Framework (NEW)
```java
// JSR-303 Bean Validation
public record NodeInput(
    UUID id,
    
    @NotBlank(message = "Label is required")
    @Size(min = 2, max = 100)
    String label,
    
    @Valid
    @NotNull
    Map<String, @NotNull Object> properties,
    
    @Size(max = 10000, message = "Content too large")
    String vectorizableContent
) {}

// Custom validator
@Constraint(validatedBy = NodeTypeValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNodeType {
    String message() default "Invalid node type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Framework validates automatically
try {
    Node node = indexer.indexNode(nodeInput);
} catch (ValidationException e) {
    // Handle validation errors
    e.getConstraintViolations().forEach(v -> 
        logger.error("Validation error: {}", v.getMessage())
    );
}
```

### 4.11 Observability & Monitoring (NEW)
```java
// Metrics (Micrometer)
@Component
public class KnowledgeIndexerMetrics {
    private final MeterRegistry registry;
    
    @Timed(value = "cef.index.node", description = "Time to index a node")
    public Node indexNode(NodeInput input) {
        Counter.builder("cef.nodes.indexed")
            .tag("label", input.label())
            .register(registry)
            .increment();
        // ... indexing logic
    }
}

// Slow query detection
@Configuration
public class QueryLoggingConfig {
    @Bean
    public SlowQueryDetector slowQueryDetector() {
        return new SlowQueryDetector(Duration.ofSeconds(1), 
            query -> logger.warn("Slow query detected: {} took {}ms", 
                query, query.getDuration()));
    }
}

// Distributed tracing (OpenTelemetry)
@WithSpan
public SearchResult retrieve(RetrievalRequest request) {
    Span span = Span.current();
    span.setAttribute("query", request.query());
    span.setAttribute("depth", request.depth());
    // ... retrieval logic
}

// Health checks
@Component
public class KnowledgeGraphHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        long nodeCount = memoryGraph.getNodeCount();
        long edgeCount = memoryGraph.getEdgeCount();
        
        return Health.up()
            .withDetail("nodes", nodeCount)
            .withDetail("edges", edgeCount)
            .withDetail("memory_usage_mb", getMemoryUsage())
            .build();
    }
}
```

### 4.12 Connection Pooling (NEW)
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
      
cef:
  storage:
    pool:
      enabled: true
      max-size: 20
      min-size: 5
      validation-query: "SELECT 1"
      test-on-borrow: true
      test-while-idle: true
```

---

## 5. Medical Domain Example

### 5.1 Domain Entities (User Defines)
- **Nodes**: Patient, Doctor, Condition, Medication, Appointment, Symptom
- **Edges**: HAS_CONDITION, TREATS, PRESCRIBED_MEDICATION, HAS_SYMPTOM, PART_OF_BODY

### 5.2 Sample Data
- 50 patients with realistic data (Faker-generated)
- 10 doctors (specializations: cardiology, neurology, general practice)
- 30 conditions with ICD-10 codes
- 50 medications
- 100 appointments with clinical notes
- 40 symptoms

### 5.3 Unstructured Documents
- 10 medical research papers (diabetes, hypertension)
- 10 clinical guidelines (treatment protocols)
- 20 patient case studies (anonymized)
- 15 drug information sheets

All documents auto-chunked (512 tokens, 50 overlap) and vectorized.

### 5.4 React Frontend
```
┌─────────────────────────────────────────────────────┐
│  Header: CEF Medical Assistant                      │
│  [Model: Ollama/Llama3 ▼] [DB: PostgreSQL ▼]       │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────┐  ┌──────────────────────────┐ │
│  │  Chat Messages   │  │  Context Graph           │ │
│  │                  │  │  (react-force-graph)     │ │
│  │  User: symptoms? │  │                          │ │
│  │  AI: Based on... │  │  [Interactive Graph]     │ │
│  │  [Context: 5]    │  │  • Patient (highlighted) │ │
│  └──────────────────┘  │  • Conditions (3)        │ │
│  [Input Box]  [Send]   │  • Related nodes         │ │
└─────────────────────────────────────────────────────┘
```

**Features:**
- Real-time streaming responses
- Graph visualization with react-force-graph-2d
- Context highlighting (which chunks/nodes were used)
- Switch database (DuckDB ↔ PostgreSQL) live
- Switch LLM provider (OpenAI ↔ Ollama) live

---

## 6. Technology Stack

### Backend
- **Java 17+**, Spring Boot 3.x, Spring AI
- **JGraphT 1.5.2** (in-memory graph)
- **PostgreSQL 15+** + pgvector (primary), **DuckDB** + VSS (alternative)
- **ANTLR 4.13.1** (parsing), SnakeYAML, OpenCSV, Jackson
- **AWS SDK S3** (MinIO support)
- **Hibernate Validator** (JSR-303 Bean Validation)
- **Flyway/Liquibase** (schema migrations)
- **HikariCP** (connection pooling)
- **Micrometer** (metrics), **OpenTelemetry** (tracing)
- **Caffeine/Redis** (L2 cache)

### Frontend
- **React 18+**, TypeScript 5+, Vite
- **TailwindCSS**, React Query, Zustand
- **react-force-graph-2d** (graph visualization)
- **Framer Motion** (animations)

### Infrastructure
- **Docker Compose**: PostgreSQL, Ollama, MinIO, Backend, Frontend
- **Ollama**: Local LLM (llama3, nomic-embed-text)

---

## 7. Success Criteria

### Functional
- ✅ Index 1000+ nodes, 5000+ edges in <10s
- ✅ Vector search <100ms for 10K chunks (PostgreSQL/DuckDB)
- ✅ Graph traversal depth 5 in <50ms (JGraphT in-memory)
- ✅ Hybrid search returns results or falls back correctly
- ✅ Multi-query context assembly within token budget
- ✅ Switch GraphStore (JGraphT ↔ Neo4j) via config
- ✅ Switch VectorStore (Postgres ↔ DuckDB ↔ Qdrant ↔ Pinecone) via config
- ✅ Switch LLM (OpenAI ↔ Ollama ↔ vLLM) without code change
- ✅ Lifecycle hooks execute in correct order
- ✅ L1 cache reduces DB queries by >80%
- ✅ L2 cache hit rate >70% for read-heavy workloads
- ✅ Batch indexing handles 10K+ nodes in single transaction
- ✅ Schema migrations run automatically on startup
- ✅ Validation catches invalid data before persistence
- ✅ Slow queries (>1s) logged with details
- ✅ Health endpoint reports system status

### Non-Functional
- **Performance**: 10K nodes + 50K edges in ~35MB memory
- **Test Coverage**: >80% for framework core
- **Documentation**: Every public API documented (JavaDoc)
- **Usability**: Example runs with `docker-compose up`
- **Cache Hit Rate**: >70% for L2 cache
- **Query Performance**: 95th percentile <100ms
- **Connection Pool**: Max 20 connections, min 5 idle
- **Metrics**: Exported to Prometheus/Grafana

### Presentation (JUGBD)
- ✅ Live demo: Chat about medical data, show graph visualization
- ✅ Demonstrate DB switching (PostgreSQL → DuckDB)
- ✅ Demonstrate provider switching (OpenAI → Ollama)
- ✅ Show reasoning context in graph (how answer was derived)
- ✅ Attendees can run example locally

---

## 8. Timeline (10 Weeks)

| Phase | Weeks | Deliverable |
|-------|-------|-------------|
| **Framework Core** | 1-3 | Node/Edge/Chunk entities, KnowledgeIndexer, InMemoryKnowledgeGraph (JGraphT), Spring Data repos, Lifecycle hooks, Validation |
| **Retrieval & Search** | 4-5 | KnowledgeRetriever, Intelligent search (3-level fallback), ReasoningContext extraction, Query DSL, Caching (L1/L2) |
| **Parser & DataSource** | 6 | AbstractParser, ParserFactory, FileSystemDataSource, BlobStorageDataSource (MinIO), Schema migrations |
| **LLM & Context Provider** | 7 | LLMClientFactory, DefaultContextProviderTool (multi-query + token budget), Observability (metrics, tracing) |
| **Medical Example** | 8 | Domain model, seed data (50 patients, 10 doctors, 30 conditions), documents, Listeners, Custom validators |
| **React Frontend** | 9 | Chat UI, graph visualization (react-force-graph), live DB/LLM switching, Health dashboard |
| **Polish & Demo** | 10 | Docker Compose, documentation, presentation deck, rehearsal, Performance tuning |

---

## 9. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| JGraphT memory limits (>100K nodes) | Medium | Document limits, implement pagination if needed |
| pgvector performance with large datasets | Medium | HNSW indexing, connection pooling, caching |
| Ollama availability/model size | Low | Bundle models with Docker, fallback to Mock |
| Graph rendering performance (1000+ nodes) | Medium | Lazy loading, clustering, level-of-detail |
| Scope creep | High | Strict MVP, defer enhancements to post-JUGBD |
| Cache consistency issues | Medium | Implement proper eviction strategy, use distributed cache for multi-instance |
| Complex lifecycle hook ordering | Medium | Document execution order, provide debugging tools |
| Migration conflicts in team development | Low | Use version control for migrations, automated conflict detection |

---

## 10. Out of Scope

The framework does **NOT** provide:
- ❌ Domain-specific schemas or validation (user's responsibility)
- ❌ Authentication/authorization (app-level concern)
- ❌ UI components (example provides reference implementation)
- ❌ Business workflows or rules
- ❌ Production deployment configs (example shows local dev only)
- ❌ Multiple database adapters beyond PostgreSQL+DuckDB (Neo4j is stretch goal)
- ❌ Distributed transactions (XA/2PC)
- ❌ Sharding/partitioning strategies
- ❌ Multi-master replication

---

## 11. References

- **ADR-002**: Complete technical architecture and design decisions
- **ADR-001**: Original problem statement and context (superseded by ADR-002)
- **JGraphT**: https://jgrapht.org/
- **pgvector**: https://github.com/pgvector/pgvector
- **Spring AI**: https://spring.io/projects/spring-ai
- **Model Context Protocol (MCP)**: https://modelcontextprotocol.io/

---

**Document Owner**: CEF Project Team  
**Last Updated**: November 24, 2025  
**Status**: Approved for Implementation
