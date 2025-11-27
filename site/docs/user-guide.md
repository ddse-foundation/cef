# CEF User Guide: ORM for Context Engineering

**Version:** beta-0.5  
**Date:** November 27, 2025  
**Audience:** Java Developers Integrating CEF

---

## Introduction

Welcome to the Context Engineering Framework (CEF) - the **Hibernate for LLM Context**. This guide helps you integrate CEF into your application as an ORM layer for knowledge models, just as you would integrate Hibernate for transactional data models.

### Core Philosophy

**Traditional ORM (Hibernate/JPA)**
- Define entities (`@Entity`, `@Table`)
- Map relationships (`@OneToMany`, `@ManyToOne`)
- Persist/query data (`EntityManager.persist()`, `em.find()`)
- Purpose: Manage **transactional data** (orders, users, products)

**Knowledge ORM (CEF)**
- Define knowledge entities (`Node`, `Edge`)
- Map semantic relationships (`RelationType`, `RelationSemantics`)
- Index/retrieve context (`indexer.indexNode()`, `retriever.retrieve()`)
- Purpose: Manage **knowledge models** for LLM context

### When to Use CEF

✅ **Use CEF** when your LLM needs:
- Structured knowledge with relationships (not just documents)
- Entity-aware retrieval ("John's medical history")
- Multi-hop reasoning ("patients similar to John")
- Domain-specific context assembly

❌ **Don't Use CEF** if you only need:
- Simple document search (use pure vector database)
- Unstructured text without entities
- One-shot question answering without context

> API note (beta-0.5): The canonical types are `Node`, `Edge`, and `Chunk`, plus `RelationType(name, sourceLabel, targetLabel, semantics, directed)` with enum values `HIERARCHICAL`, `ASSOCIATIVE`, `CAUSAL`, `TEMPORAL`, `SPATIAL`, and `CUSTOM`. Some snippets below reflect earlier enum names; use the [Hands-On tutorial](tutorials/build-your-first-model.md) for the exact code paths exercised by the tests.

---

## Table of Contents

1. [Quick Integration](#quick-integration)
2. [Defining Knowledge Models](#defining-knowledge-models)
3. [Setting Up ORM Layer](#setting-up-orm-layer)
4. [Indexing Knowledge](#indexing-knowledge)
5. [Retrieving Context](#retrieving-context)
6. [Advanced Patterns](#advanced-patterns)
7. [Production Deployment](#production-deployment)
8. [Troubleshooting](#troubleshooting)

---

## Quick Integration

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>org.ddse.ml</groupId>
    <artifactId>cef-framework</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Step 2: Configure Application

Create `application.yml`:

```yaml
cef:
  # Storage backends (like configuring Hibernate datasource)
  graph:
    store: jgrapht  # in-memory for &lt;100K entities
    preload-on-startup: true
  
  vector:
    store: postgres  # persistent semantic search
    dimension: 768
  
  # Embedding provider (like configuring JPA dialect)
  embedding:
    provider: ollama
    model: nomic-embed-text
    base-url: http://localhost:11434

spring:
  # R2DBC connection (like JPA datasource)
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: postgres
    password: postgres
```

### Step 3: Initialize Knowledge Model

```java
@Configuration
public class KnowledgeModelConfig {
    
    @Autowired
    private KnowledgeIndexer indexer;
    
    @PostConstruct
    public void initializeKnowledgeModel() {
        // Define relationship semantics (like @Entity mapping)
        List<RelationType> relationTypes = List.of(
            new RelationType("TREATS", RelationSemantics.ASSOCIATION, true, 
                "Doctor treats patient"),
            new RelationType("HAS_CONDITION", RelationSemantics.ATTRIBUTION, false,
                "Patient has medical condition"),
            new RelationType("PRESCRIBED_MEDICATION", RelationSemantics.CAUSALITY, false,
                "Patient prescribed medication")
        );
        
        // Initialize framework (like EntityManagerFactory setup)
        indexer.initialize(List.of(), relationTypes).block();
        
        log.info("Knowledge model initialized with {} relation types", 
            relationTypes.size());
    }
}
```

### Step 4: Use ORM Services

```java
@Service
public class PatientService {
    
    @Autowired
    private KnowledgeIndexer indexer;  // Like EntityManager
    
    @Autowired
    private KnowledgeRetriever retriever;  // Like Repository
    
    public Mono<Node> createPatient(PatientDTO dto) {
        // Similar to em.persist(entity)
        NodeInput input = new NodeInput(
            null,  // auto-generate ID
            "Patient",
            Map.of(
                "name", dto.getName(),
                "age", dto.getAge(),
                "gender", dto.getGender()
            ),
            dto.getMedicalHistory()  // vectorizable content
        );
        
        return indexer.indexNode(input);
    }
    
    public Mono<SearchResult> findPatientContext(String patientName) {
        // Similar to repository.findByName()
        GraphQuery query = new GraphQuery(
            "Find medical context for " + patientName,
            List.of(new ResolutionTarget(patientName, "Patient", 1)),
            new TraversalHint(List.of("HAS_CONDITION", "PRESCRIBED_MEDICATION"), 2, null),
            null, null, null,
            3, 10, true, 4000, 100, 100
        );
        
        return retriever.retrieve(query);
    }
}
```

---

## Defining Knowledge Models

### Philosophy: Knowledge Model vs Data Model

**Data Model** (Hibernate):
```java
@Entity
@Table(name = "patients")
public class Patient {
    @Id
    private Long id;
    
    @Column
    private String name;
    
    @ManyToOne
    @JoinColumn(name = "doctor_id")
    private Doctor treatingDoctor;
}
```

**Knowledge Model** (CEF):
```java
// Framework provides primitives
public class Node {
    UUID id;                        // Entity identifier
    String label;                   // Type (like @Entity name)
    Map<String, Object> properties; // Attributes (like @Column)
    String vectorizableContent;     // Semantic content
}

public class Edge {
    UUID id;
    String relationType;            // Relationship (like @ManyToOne)
    UUID sourceNodeId;
    UUID targetNodeId;
}

// You define semantics
public class RelationType {
    String name;                    // "TREATS", "HAS_CONDITION"
    RelationSemantics semantics;    // How framework understands it
    boolean bidirectional;
}
```

### Mapping Your Domain

#### 1. Identify Entities (Nodes)

Map domain entities to `Node` labels:

```java
// Medical Domain
"Patient" → Node(label="Patient", properties={name, age, gender})
"Doctor" → Node(label="Doctor", properties={name, specialization})
"Condition" → Node(label="Condition", properties={name, icd10Code})
"Medication" → Node(label="Medication", properties={name, dosage})

// E-Commerce Domain
"Product" → Node(label="Product", properties={name, price, category})
"Customer" → Node(label="Customer", properties={name, email})
"Order" → Node(label="Order", properties={orderDate, total})

// Legal Domain
"Case" → Node(label="Case", properties={caseNumber, court, year})
"Statute" → Node(label="Statute", properties={code, title})
"Precedent" → Node(label="Precedent", properties={citation, court})
```

#### 2. Define Relationships (Edges)

Map relationships with semantic hints:

```java
@Configuration
public class DomainRelationships {
    
    public List<RelationType> medicalRelations() {
        return List.of(
            // Hierarchical relationships (parent-child)
            new RelationType("IS_PART_OF", RelationSemantics.HIERARCHY, true,
                "Organ is part of body system"),
            
            // Classification relationships (instance-class)
            new RelationType("IS_TYPE_OF", RelationSemantics.CLASSIFICATION, false,
                "Specific condition is type of general condition"),
            
            // Association relationships (peer-to-peer)
            new RelationType("TREATS", RelationSemantics.ASSOCIATION, true,
                "Doctor treats patient"),
            
            // Attribution relationships (has/owns)
            new RelationType("HAS_CONDITION", RelationSemantics.ATTRIBUTION, false,
                "Patient has condition"),
            
            // Causal relationships (cause-effect)
            new RelationType("PRESCRIBED_MEDICATION", RelationSemantics.CAUSALITY, false,
                "Treatment prescribed for condition"),
            
            // Temporal relationships (sequence)
            new RelationType("FOLLOWS", RelationSemantics.TEMPORAL, false,
                "Treatment follows diagnosis")
        );
    }
}
```

**Why Semantics Matter:**
Framework uses `RelationSemantics` to optimize traversal:
- **HIERARCHY**: Navigate parents/children (e.g., "get all sub-conditions")
- **CLASSIFICATION**: Find sibling instances (e.g., "similar patients")
- **ASSOCIATION**: Explore related entities (e.g., "doctor's patients")
- **CAUSALITY**: Follow cause-effect chains (e.g., "condition → treatment")

#### 3. Vectorizable Content

Like defining `@Lob` fields in JPA, specify what content should be semantically searchable:

```java
// Good vectorizable content
NodeInput patient = new NodeInput(
    null, "Patient",
    Map.of("name", "John Doe", "age", 45),
    // Rich textual description
    "45-year-old male with history of type 2 diabetes, hypertension, " +
    "and hyperlipidemia. Recently experienced chest pain and shortness of breath."
);

// Bad vectorizable content (too short/generic)
NodeInput patient = new NodeInput(
    null, "Patient",
    Map.of("name", "John Doe"),
    "Patient"  // Too generic, won't help retrieval
);
```

---

## Setting Up ORM Layer

### 1. Repository Pattern (Recommended)

Create domain-specific facades over CEF ORM:

```java
@Repository
public class PatientKnowledgeRepository {
    
    private final KnowledgeIndexer indexer;
    private final KnowledgeRetriever retriever;
    
    // CREATE
    public Mono<UUID> createPatient(PatientDTO dto) {
        NodeInput node = toNodeInput(dto);
        return indexer.indexNode(node)
            .map(Node::getId);
    }
    
    // READ - by ID
    public Mono<PatientDTO> findPatientById(UUID id) {
        return retriever.findNode(id)
            .map(this::toDTO);
    }
    
    // READ - by property
    public Flux<PatientDTO> findPatientsByAge(int age) {
        return retriever.findNodesByProperty("age", age)
            .map(this::toDTO);
    }
    
    // READ - with relationships
    public Mono<PatientContext> findPatientWithConditions(UUID patientId) {
        return retriever.getChildren(patientId)  // Follow HAS_CONDITION edges
            .collectList()
            .map(conditions -> new PatientContext(patientId, conditions));
    }
    
    // UPDATE
    public Mono<Node> updatePatient(UUID id, Map<String, Object> updates) {
        return indexer.updateNode(id, updates);
    }
    
    // DELETE
    public Mono<Void> deletePatient(UUID id) {
        return indexer.deleteNode(id, true);  // cascade edges
    }
    
    // Complex Query - like JPQL
    public Mono<SearchResult> findPatientsWithSimilarConditions(UUID patientId) {
        // 1. Get patient's conditions
        return retriever.getChildren(patientId)
            // 2. Find patients with same conditions (siblings in graph)
            .flatMap(condition -> retriever.getSiblings(condition.getId()))
            .distinct()
            // 3. Assemble into SearchResult
            .collectList()
            .map(this::toSearchResult);
    }
    
    // Helper methods
    private NodeInput toNodeInput(PatientDTO dto) {
        return new NodeInput(
            dto.getId(),
            "Patient",
            Map.of(
                "name", dto.getName(),
                "age", dto.getAge(),
                "gender", dto.getGender()
            ),
            dto.getMedicalHistory()
        );
    }
    
    private PatientDTO toDTO(Node node) {
        return new PatientDTO(
            node.getId(),
            (String) node.getProperties().get("name"),
            (Integer) node.getProperties().get("age"),
            (String) node.getProperties().get("gender"),
            node.getVectorizableContent()
        );
    }
}
```

### 2. Service Layer Pattern

Add business logic on top of ORM:

```java
@Service
@Transactional  // CEF supports transactional indexing
public class MedicalKnowledgeService {
    
    @Autowired
    private PatientKnowledgeRepository patientRepo;
    
    @Autowired
    private KnowledgeIndexer indexer;
    
    /**
     * Create patient with all related entities (like cascading persist)
     */
    public Mono<UUID> registerPatient(PatientRegistrationDTO dto) {
        return Mono.defer(() -> {
            // 1. Create patient node
            NodeInput patientNode = new NodeInput(
                null, "Patient",
                Map.of("name", dto.getName(), "age", dto.getAge()),
                dto.getMedicalHistory()
            );
            
            return indexer.indexNode(patientNode)
                .flatMap(patient -> {
                    List<Mono<Edge>> edgeCreations = new ArrayList<>();
                    
                    // 2. Create edges to doctor
                    if (dto.getDoctorId() != null) {
                        EdgeInput treatsEdge = new EdgeInput(
                            null, "TREATS",
                            dto.getDoctorId(), patient.getId(),
                            Map.of("since", LocalDate.now()),
                            1.0
                        );
                        edgeCreations.add(indexer.indexEdge(treatsEdge));
                    }
                    
                    // 3. Create edges to conditions
                    for (UUID conditionId : dto.getConditionIds()) {
                        EdgeInput hasCondition = new EdgeInput(
                            null, "HAS_CONDITION",
                            patient.getId(), conditionId,
                            Map.of("diagnosedDate", LocalDate.now()),
                            1.0
                        );
                        edgeCreations.add(indexer.indexEdge(hasCondition));
                    }
                    
                    // 4. Wait for all edges
                    return Flux.merge(edgeCreations)
                        .then(Mono.just(patient.getId()));
                });
        });
    }
    
    /**
     * Get comprehensive patient context (like JPQL join fetch)
     */
    public Mono<PatientContextDTO> getPatientContext(UUID patientId) {
        GraphQuery query = new GraphQuery(
            "Get patient comprehensive context",
            List.of(new ResolutionTarget(patientId.toString(), "Patient", 1)),
            new TraversalHint(
                List.of("HAS_CONDITION", "PRESCRIBED_MEDICATION", "TREATED_BY"),
                2,  // 2-hop traversal
                null
            ),
            null, null, null,
            3, 20, true, 4000, 100, 100
        );
        
        return retriever.retrieve(query)
            .map(result -> {
                ReasoningContext ctx = result.reasoningContext();
                return new PatientContextDTO(
                    ctx.rootNode(),
                    extractConditions(ctx.relatedNodes()),
                    extractMedications(ctx.relatedNodes()),
                    extractDoctors(ctx.relatedNodes()),
                    result.results()  // semantic chunks
                );
            });
    }
}
```

### 3. Lifecycle Hooks (Like JPA Callbacks)

Implement entity lifecycle management:

```java
@Component
public class KnowledgeEntityListener {
    
    @Autowired
    private AuditService auditService;
    
    @PrePersist
    public void beforeIndexNode(NodeInput input) {
        log.info("Indexing node: {}", input.label());
        validateNodeInput(input);
    }
    
    @PostPersist
    public void afterIndexNode(Node node) {
        auditService.logNodeCreation(node);
        notifySearchIndexUpdate(node);
    }
    
    @PreUpdate
    public void beforeUpdateNode(UUID nodeId, Map<String, Object> changes) {
        auditService.logNodeUpdate(nodeId, changes);
    }
    
    @PreRemove
    public void beforeDeleteNode(UUID nodeId) {
        // Check for dependent entities
        long edgeCount = retriever.getNeighbors(nodeId, 1)
            .count().block();
        
        if (edgeCount > 0) {
            log.warn("Deleting node {} with {} relationships", nodeId, edgeCount);
        }
    }
}
```

---

## Indexing Knowledge

### Batch Indexing (Like Hibernate's StatelessSession)

For initial loading or bulk operations:

```java
@Service
public class KnowledgeBulkLoader {
    
    @Autowired
    private KnowledgeIndexer indexer;
    
    /**
     * Bulk load knowledge from data source
     * Similar to Hibernate's batch insert
     */
    public Mono<IndexResult> bulkLoadPatients(List<PatientDTO> patients) {
        // 1. Convert to node inputs
        List<NodeInput> nodeInputs = patients.stream()
            .map(this::toNodeInput)
            .toList();
        
        // 2. Batch index (optimized with parallel embedding)
        BatchInput batch = new BatchInput(nodeInputs, List.of(), List.of());
        
        return indexer.indexBatch(batch)
            .doOnSuccess(result -> 
                log.info("Indexed {} patients in {}ms", 
                    result.nodesIndexed(), result.durationMs())
            );
    }
    
    /**
     * Full index from data source (like Hibernate's schema export)
     */
    public Mono<IndexResult> fullIndexFromFiles(String dataDir) {
        DataSource dataSource = new FileSystemDataSource(dataDir);
        
        return indexer.fullIndex(dataSource)
            .doOnSuccess(result -> {
                log.info("Full index complete:");
                log.info("  Nodes: {}", result.nodesIndexed());
                log.info("  Edges: {}", result.edgesIndexed());
                log.info("  Chunks: {}", result.chunksIndexed());
                log.info("  Duration: {}ms", result.durationMs());
            });
    }
}
```

### Incremental Updates (Like JPA Merge)

```java
@Service
public class PatientUpdateService {
    
    @Autowired
    private KnowledgeIndexer indexer;
    
    /**
     * Update patient information
     * Similar to entityManager.merge(entity)
     */
    @Transactional
    public Mono<Node> updatePatientMedicalHistory(
        UUID patientId, 
        String newHistory
    ) {
        // 1. Get current node
        return retriever.findNode(patientId)
            .flatMap(currentNode -> {
                // 2. Merge changes
                Map<String, Object> updates = new HashMap<>(currentNode.getProperties());
                updates.put("lastUpdate", LocalDateTime.now());
                
                // 3. Update with new vectorizable content
                return indexer.updateNode(patientId, updates)
                    .flatMap(updated -> {
                        // 4. Re-index chunks if content changed
                        if (!newHistory.equals(currentNode.getVectorizableContent())) {
                            ChunkInput chunk = new ChunkInput(
                                null, newHistory, patientId,
                                Map.of("type", "medical_history")
                            );
                            return indexer.indexChunk(chunk)
                                .thenReturn(updated);
                        }
                        return Mono.just(updated);
                    });
            });
    }
    
    /**
     * Add relationship (like adding to @ManyToMany collection)
     */
    public Mono<Edge> addConditionToPatient(UUID patientId, UUID conditionId) {
        EdgeInput edge = new EdgeInput(
            null, "HAS_CONDITION",
            patientId, conditionId,
            Map.of("diagnosedDate", LocalDate.now()),
            1.0
        );
        
        return indexer.indexEdge(edge);
    }
}
```

---

## Retrieving Context

### Basic Queries (Like JPQL)

```java
@Service
public class PatientQueryService {
    
    @Autowired
    private KnowledgeRetriever retriever;
    
    // Find by ID (like em.find())
    public Mono<Node> findById(UUID id) {
        return retriever.findNode(id);
    }
    
    // Find by label (like SELECT p FROM Patient p)
    public Flux<Node> findAllPatients() {
        return retriever.findNodesByLabel("Patient");
    }
    
    // Find by property (like WHERE clause)
    public Flux<Node> findPatientsByAge(int age) {
        return retriever.findNodesByProperty("age", age);
    }
    
    // Navigate relationships (like JOIN FETCH)
    public Flux<Node> getPatientConditions(UUID patientId) {
        return retriever.getChildren(patientId);
    }
}
```

### Complex Context Retrieval

The power of CEF ORM - assemble rich context automatically:

```java
@Service
public class ContextRetrievalService {
    
    @Autowired
    private KnowledgeRetriever retriever;
    
    /**
     * Entity-aware retrieval
     * "Find John's treatment recommendations"
     */
    public Mono<SearchResult> getPatientTreatmentContext(String patientName) {
        GraphQuery query = new GraphQuery(
            "Find treatment recommendations for patient",
            // Resolve entity by name
            List.of(new ResolutionTarget(
                patientName, 
                "Patient",  // type hint
                1           // expect 1 match
            )),
            // Traverse relationships
            new TraversalHint(
                List.of("HAS_CONDITION", "PRESCRIBED_MEDICATION"),
                2,  // 2-hop depth
                Set.of(RelationSemantics.ATTRIBUTION, RelationSemantics.CAUSALITY)
            ),
            null, null, null,
            3,    // minResults for fallback
            10,   // topK chunks
            true, // include reasoning context
            4000, // token budget
            100, 100
        );
        
        return retriever.retrieve(query);
    }
    
    /**
     * Multi-hop reasoning
     * "Find patients similar to John"
     */
    public Mono<SearchResult> findSimilarPatients(UUID patientId) {
        // Strategy: 
        // 1. Get patient's conditions
        // 2. Find other patients with same conditions (siblings in graph)
        // 3. Rank by condition overlap
        
        return retriever.extractContext(
            Set.of(patientId),
            new GraphTraversal(
                3,  // depth
                List.of("HAS_CONDITION"),
                null
            )
        ).flatMap(context -> {
            // Use reasoning context to enhance query
            Set<String> keywords = context.contextKeywords();
            
            GraphQuery query = new GraphQuery(
                "Find patients with similar conditions: " + 
                    String.join(", ", keywords),
                List.of(),  // no specific targets
                new TraversalHint(
                    List.of("HAS_CONDITION"),
                    1,
                    Set.of(RelationSemantics.ATTRIBUTION)
                ),
                null, null, null,
                3, 10, true, 4000, 100, 100
            );
            
            return retriever.retrieve(query);
        });
    }
    
    /**
     * Graph pattern matching (advanced)
     * Use new GraphPattern API for complex multi-hop queries
     */
    public Mono<SearchResult> findComorbidityPatterns() {
        // Pattern: Patient → Condition → Patient → Medication
        // (patients with conditions treated by same medications)
        
        GraphPattern pattern = new GraphPattern(
            "comorbidity_pattern",
            List.of(
                new TraversalStep("Patient", "HAS_CONDITION", "Condition", 0),
                new TraversalStep("Condition", "TREATED_WITH", "Medication", 1),
                new TraversalStep("Medication", "PRESCRIBED_TO", "Patient", 2)
            ),
            List.of(
                // Filter: only chronic conditions
                Constraint.propertyEquals("Condition", "type", "chronic", 0)
            ),
            "Find comorbidity patterns via shared medications"
        );
        
        GraphQuery query = new GraphQuery(
            "Find comorbidity patterns",
            List.of(),  // no entry point needed
            null,
            List.of(pattern),  // use pattern instead
            null,
            RankingStrategy.HYBRID,
            3, 10, true, 4000, 100, 100
        );
        
        return retriever.retrieve(query);
    }
}
```

### Automatic Fallback

CEF ORM intelligently falls back when graph traversal insufficient:

```java
// Scenario 1: Entity found, rich graph context
GraphQuery query = new GraphQuery(
    "John's diabetes treatment",
    List.of(new ResolutionTarget("John", "Patient", 1)),
    new TraversalHint(List.of("HAS_CONDITION", "PRESCRIBED_MEDICATION"), 2, null),
    ...
);
SearchResult result = retriever.retrieve(query).block();
// Strategy: HYBRID (graph traversal + vector search on related nodes)
// Result: 12 chunks from John's medical graph

// Scenario 2: Entity not found, fallback to semantic
GraphQuery query = new GraphQuery(
    "General diabetes treatment guidelines",
    List.of(),  // no entity
    null,
    ...
);
SearchResult result = retriever.retrieve(query).block();
// Strategy: VECTOR (pure semantic search)
// Result: 8 chunks about diabetes treatment

// Scenario 3: No semantic match, fallback to keyword
GraphQuery query = new GraphQuery(
    "XYZ-123 medication protocol",  // rare term
    List.of(),
    null,
    ...
);
SearchResult result = retriever.retrieve(query).block();
// Strategy: BM25 (keyword search)
// Result: 3 chunks containing "XYZ-123"
```

---

## Advanced Patterns

### 1. Caching Strategy (Planned for v0.6)

*Note: Distributed caching is currently on the roadmap. The following configuration demonstrates the planned API.*

```java
@Configuration
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "nodes", "search-results", "reasoning-contexts"
        );
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
        );
        return manager;
    }
}
```

### 2. Multi-Tenancy (Planned for v0.6)

```java
@Configuration
public class MultiTenantConfig {
    
    @Bean
    public TenantKnowledgeIndexer tenantIndexer(KnowledgeIndexer baseIndexer) {
        return new TenantKnowledgeIndexer(baseIndexer);
    }
}
```

### 3. Validation Framework (Like Bean Validation)

```java
@Validated
public class ValidatedKnowledgeService {
    
    public Mono<Node> indexPatient(
        @Valid @NotNull PatientNodeInput input
    ) {
        return indexer.indexNode(input.toNodeInput());
    }
}

// Custom validator
public class PatientNodeInput {
    
    @NotBlank
    private String name;
    
    @Min(0) @Max(150)
    private int age;
    
    @Pattern(regexp = "M|F|O")
    private String gender;
    
    @NotBlank
    @Size(min = 50, message = "Medical history too short for meaningful context")
    private String medicalHistory;
    
    public NodeInput toNodeInput() {
        return new NodeInput(
            null, "Patient",
            Map.of("name", name, "age", age, "gender", gender),
            medicalHistory
        );
    }
}
```

### 4. Schema Evolution (Like Flyway/Liquibase)

```java
@Component
public class KnowledgeSchemaEvolution {
    
    @Autowired
    private KnowledgeIndexer indexer;
    
    @Autowired
    private KnowledgeRetriever retriever;
    
    /**
     * Version 1 → Version 2: Add ICD-10 codes to conditions
     */
    @PostConstruct
    @Order(1)
    public void migrateToV2() {
        retriever.findNodesByLabel("Condition")
            .flatMap(condition -> {
                Map<String, Object> props = condition.getProperties();
                
                if (!props.containsKey("icd10Code")) {
                    // Add missing field
                    props.put("icd10Code", inferICD10Code(condition));
                    return indexer.updateNode(condition.getId(), props);
                }
                
                return Mono.just(condition);
            })
            .count()
            .subscribe(count -> 
                log.info("Migrated {} conditions to v2", count)
            );
    }
    
    /**
     * Version 2 → Version 3: Split Medication into Generic/Brand
     */
    @PostConstruct
    @Order(2)
    public void migrateToV3() {
        // Similar pattern for schema evolution
    }
}
```

---

## Production Deployment

### 1. Monitoring & Observability

```java
@Configuration
public class ObservabilityConfig {
    
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}

@Service
public class MonitoredRetrievalService {
    
    @Autowired
    private KnowledgeRetriever retriever;
    
    @Autowired
    private MeterRegistry registry;
    
    @Timed(value = "cef.retrieval.duration", description = "Knowledge retrieval duration")
    public Mono<SearchResult> retrieve(GraphQuery query) {
        return retriever.retrieve(query)
            .doOnSuccess(result -> {
                // Record strategy used
                registry.counter("cef.retrieval.strategy", 
                    "strategy", result.strategy().name()).increment();
                
                // Record result count
                registry.summary("cef.retrieval.results")
                    .record(result.results().size());
                
                // Record graph traversal depth
                if (result.reasoningContext() != null) {
                    registry.summary("cef.graph.depth")
                        .record(result.reasoningContext().depth());
                }
            });
    }
}
```

### 2. Health Checks

```java
@Component
public class CefHealthIndicator implements HealthIndicator {
    
    @Autowired
    private GraphStore graphStore;
    
    @Autowired
    private VectorStore vectorStore;
    
    @Override
    public Health health() {
        try {
            long nodeCount = graphStore.getNodeCount();
            long edgeCount = graphStore.getEdgeCount();
            long chunkCount = vectorStore.getChunkCount();
            
            return Health.up()
                .withDetail("graph_store", graphStore.getImplementation())
                .withDetail("vector_store", vectorStore.getImplementation())
                .withDetail("node_count", nodeCount)
                .withDetail("edge_count", edgeCount)
                .withDetail("chunk_count", chunkCount)
                .withDetail("avg_degree", edgeCount / (double) nodeCount)
                .build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

### 3. Performance Tuning

```yaml
cef:
  # Graph optimization
  graph:
    jgrapht:
      max-nodes: 100000      # Increase if needed
      preload-on-startup: true
      cache-enabled: true
  
  # Vector optimization
  vector:
    postgres:
      connection-pool-size: 20
      batch-size: 500
  
  # Cache tuning
  cache:
    l1:
      enabled: true
      max-size: 10000
      ttl-seconds: 1800
    l2:
      enabled: true
      type: redis
      redis:
        host: redis-cluster
        ttl-seconds: 3600
  
  # Search optimization
  search:
    default-top-k: 10
    max-graph-depth: 3
    enable-query-cache: true
```

---

## Troubleshooting

### Common Issues

#### Issue 1: Slow Retrieval

**Symptoms:**
- Retrieval taking >2 seconds
- High CPU during graph traversal

**Diagnosis:**
```java
@Autowired
private MeterRegistry registry;

public void diagnoseSlowRetrieval() {
    registry.find("cef.retrieval.duration").timers()
        .forEach(timer -> {
            log.info("Retrieval duration: mean={}ms, max={}ms",
                timer.mean(TimeUnit.MILLISECONDS),
                timer.max(TimeUnit.MILLISECONDS));
        });
}
```

**Solutions:**
1. **Reduce traversal depth**: Change `depth=3` to `depth=2`
2. **Add caching**: Enable L2 cache for frequent queries
3. **Optimize graph**: Switch from JGraphT to Neo4j for >100K nodes
4. **Limit result set**: Reduce `topK` from 20 to 10

#### Issue 2: Out of Memory (JGraphT)

**Symptoms:**
- `OutOfMemoryError` during graph loading
- Heap size constantly at 80%+

**Diagnosis:**
```bash
jcmd <pid> GC.heap_info
```

**Solutions:**
1. **Increase heap**: `-Xmx4g` → `-Xmx8g`
2. **Switch to Neo4j**:
```yaml
cef:
  graph:
    store: neo4j  # Disk-based, no memory limit
```
3. **Lazy loading**: Disable preload
```yaml
cef:
  graph:
    jgrapht:
      preload-on-startup: false
```

#### Issue 3: Embedding Generation Slow

**Symptoms:**
- Indexing 1000 nodes takes >5 minutes
- Embedding service timeout errors

**Solutions:**
1. **Batch embeddings**:
```java
indexer.indexBatch(new BatchInput(
    nodeInputs,
    edgeInputs,
    chunkInputs,
    4  // parallelism
));
```
2. **Use faster provider**:
```yaml
cef:
  embedding:
    provider: openai  # Faster than local Ollama
    model: text-embedding-3-small
```

---

## Summary

CEF provides an ORM abstraction for knowledge models just as Hibernate provides for transactional data:

**Key Concepts:**
- **Nodes** → Entities (like `@Entity`)
- **Edges** → Relationships (like `@OneToMany`)
- **RelationType** → Semantic mapping (like `@JoinColumn`)
- **KnowledgeIndexer** → Persistence API (like `EntityManager`)
- **KnowledgeRetriever** → Query API (like `Repository`)

**Best Practices:**
1. Define knowledge models declaratively
2. Use repository pattern for domain facades
3. Leverage dual persistence (graph + vector)
4. Enable caching for frequently accessed contexts
5. Monitor performance metrics in production
6. Start with JGraphT, migrate to Neo4j when needed

**Next Steps:**
- Read [Architecture](architecture.md) for deep dive
- Review the [Benchmarks](benchmarks.md) page for the published analyses
- Explore test suite in `cef-framework/src/test/java` for real-world examples
- Visit [DDSE Foundation](https://ddse-foundation.github.io/) for updates and community resources

---

**Questions?** Open an issue or discussion on GitHub.

**Want to contribute?** Contact DDSE Foundation at https://ddse-foundation.github.io/
