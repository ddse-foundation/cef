---
sidebar_position: 1
---

# Knowledge Model

Understanding the core abstraction of CEF Framework.

## What is a Knowledge Model?

A **Knowledge Model** in CEF is a graph-based representation of domain entities and their relationships, designed specifically for LLM context engineering. Think of it as a schema for your knowledge, similar to how a database schema defines tables and relationships for transactional data.

### Comparison with Traditional Models

| Aspect | Traditional ORM (Hibernate) | Knowledge ORM (CEF) |
|--------|----------------------------|---------------------|
| **Purpose** | Persist transactional data | Assemble LLM context |
| **Entities** | `@Entity` classes | `Node` instances |
| **Relationships** | `@ManyToOne`, `@OneToMany` | `Edge` with semantic types |
| **Query** | SQL/JPQL | Graph traversal + vector search |
| **Storage** | Relational tables | Dual: Graph + Vector stores |
| **Access Pattern** | Random access by ID | Context assembly by query |

## Core Components

### 1. Node - The Universal Entity

Every entity in your domain becomes a `Node`:

```java
public class Node {
    UUID id;                        // Unique identifier
    String label;                   // Entity type ("Patient", "Product", etc.)
    Map<String, Object> properties; // Flexible attributes (JSONB)
    String vectorizableContent;     // Text for semantic search
    Timestamp created, updated;
}
```

**Example - Medical Domain:**

```java
Node patient = new Node(
    null,  // Auto-generate ID
    "Patient",  // Entity type
    Map.of(
        "name", "John Doe",
        "age", 45,
        "gender", "M",
        "mrn", "MRN-12345"
    ),
    // Vectorizable content - rich description for semantic search
    "45-year-old male patient with history of type 2 diabetes mellitus, " +
    "hypertension, and hyperlipidemia. Recently experienced chest pain " +
    "and shortness of breath. Currently prescribed Metformin 1000mg BID, " +
    "Lisinopril 10mg QD, and Atorvastatin 40mg QD."
);
```

**Example - E-Commerce Domain:**

```java
Node product = new Node(
    null,
    "Product",
    Map.of(
        "sku", "LAPTOP-XPS-15",
        "name", "Dell XPS 15",
        "price", 1899.99,
        "category", "Electronics",
        "stock", 42
    ),
    "Dell XPS 15 laptop with 15.6-inch 4K OLED display, Intel Core i9 " +
    "processor, 32GB RAM, 1TB NVMe SSD. Perfect for content creators and " +
    "professionals. Includes Thunderbolt 4 ports, Wi-Fi 6E, and premium " +
    "aluminum build quality."
);
```

### 2. Edge - Typed Relationships

Relationships between nodes are represented as typed edges:

```java
public class Edge {
    UUID id;
    String relationType;            // Semantic relationship type
    UUID sourceNodeId;              // Source node
    UUID targetNodeId;              // Target node
    Map<String, Object> properties; // Relationship attributes
    Double weight;                  // Optional weight for graph algorithms
    Timestamp created;
}
```

**Example - Medical Relationships:**

```java
// Patient HAS_CONDITION Diabetes
Edge hasCondition = new Edge(
    null,
    "HAS_CONDITION",
    patientId,
    diabetesId,
    Map.of(
        "diagnosedOn", "2023-01-15",
        "severity", "moderate",
        "status", "active"
    ),
    1.0
);

// Doctor TREATS Patient
Edge treats = new Edge(
    null,
    "TREATS",
    doctorId,
    patientId,
    Map.of(
        "since", "2023-01-20",
        "specialty", "endocrinology"
    ),
    1.0
);
```

### 3. RelationType - Semantic Annotations

Define relationship types with semantic hints for intelligent traversal:

```java
public class RelationType {
    String name;                    // "TREATS", "HAS_CONDITION", etc.
    String sourceLabel;             // Expected source node label
    String targetLabel;             // Expected target node label
    RelationSemantics semantics;    // Semantic category
    boolean directed;               // Is relationship directional?
}
```

**Semantic Categories:**

```java
public enum RelationSemantics {
    HIERARCHY,        // Parent-child (e.g., "IS_PART_OF")
    CLASSIFICATION,   // Type-instance (e.g., "IS_TYPE_OF")
    ASSOCIATION,      // Peer-to-peer (e.g., "KNOWS", "WORKS_WITH")
    ATTRIBUTION,      // Ownership/has (e.g., "HAS_CONDITION", "OWNS")
    CAUSALITY,        // Cause-effect (e.g., "CAUSES", "TREATS")
    TEMPORAL,         // Time-based (e.g., "FOLLOWS", "PRECEDES")
    REFERENCE         // Cross-reference (e.g., "MENTIONS", "CITES")
}
```

**Example - Medical Domain:**

```java
List<RelationType> medicalRelations = List.of(
    new RelationType("TREATS", "Doctor", "Patient", 
        RelationSemantics.ASSOCIATION, true,
        "Doctor provides treatment to patient"),
    
    new RelationType("HAS_CONDITION", "Patient", "Condition",
        RelationSemantics.ATTRIBUTION, false,
        "Patient has medical condition"),
    
    new RelationType("PRESCRIBED_MEDICATION", "Patient", "Medication",
        RelationSemantics.CAUSALITY, false,
        "Patient is prescribed medication"),
    
    new RelationType("IS_TYPE_OF", "Condition", "ConditionCategory",
        RelationSemantics.HIERARCHY, true,
        "Condition is subtype of category")
);
```

### 4. Chunk - Vectorized Content

Text chunks with embeddings for semantic search:

```java
public class Chunk {
    UUID id;
    String content;                 // Text content
    float[] embedding;              // Vector embedding
    UUID linkedNodeId;              // Optional: linked to a Node
    Map<String, Object> metadata;   // Source, author, date, etc.
    Timestamp created;
}
```

**Why Chunks?**

Nodes represent *entities*, Chunks represent *text content*:

- **Node**: "Patient John Doe" (structured entity)
- **Chunk**: "John's medical history describes..." (semantic text)

Chunks enable:
- Semantic search across unstructured text
- Large text documents split into manageable pieces
- Vector similarity matching

## Dual Persistence Architecture

CEF automatically manages dual persistence:

### Graph Store (Relationships)

Stores `Node` and `Edge` entities for:
- Fast relationship traversal (multi-hop queries)
- Graph algorithms (shortest path, neighbors)
- Structural reasoning

**Implementations:**
- **JGraphT** (default): In-memory, &lt;100K nodes, O(1) lookups
- **Neo4j** (planned): Millions of nodes, Cypher queries

### Vector Store (Semantics)

Stores `Chunk` entities with embeddings for:
- Semantic similarity search
- Full-text search
- Content retrieval

**Implementations:**
- **DuckDB** (default): Embedded, brute-force search
- **PostgreSQL + pgvector**: HNSW index, production-grade
- **Qdrant** (planned): Specialized vector database

### How They Work Together

1. **Indexing:**
   ```java
   Node patient = new Node(..., vectorizableContent);
   indexer.indexNode(patient);  // Stores in BOTH graph + vector
   ```
   
   - **Graph store**: Saves node with properties
   - **Vector store**: Chunks content, generates embeddings, saves chunks linked to node

2. **Retrieval:**
   ```java
   retriever.retrieve(query);
   ```
   
   - **Vector search**: Finds semantically similar chunks
   - **Graph traversal**: Follows edges from chunk's linked node
   - **Context assembly**: Combines graph neighborhood + semantic results

## Knowledge Model Lifecycle

### 1. Definition Phase

Define your domain entities and relationships:

```java
@Configuration
public class DomainModelConfig {
    
    @PostConstruct
    public void initializeModel() {
        List<RelationType> relations = List.of(
            new RelationType("HAS_CONDITION", "Patient", "Condition",
                RelationSemantics.ATTRIBUTION, false, "..."),
            // ... more relations
        );
        
        indexer.initialize(relations).block();
    }
}
```

### 2. Indexing Phase

Populate the knowledge model with data:

```java
// Index individual nodes
Mono<Node> savedPatient = indexer.indexNode(patientInput);

// Index edges
Mono<Edge> savedEdge = indexer.indexEdge(edgeInput);

// Bulk indexing
BatchInput batch = new BatchInput(nodes, edges, chunks);
Mono<BatchIndexResult> result = indexer.batchIndex(batch);
```

### 3. Retrieval Phase

Query the knowledge model for LLM context:

```java
RetrievalRequest request = RetrievalRequest.builder()
    .query("Find patients with diabetes on insulin therapy")
    .depth(2)  // Multi-hop traversal
    .topK(10)
    .build();

Mono<RetrievalResult> result = retriever.retrieve(request);
```

### 4. Maintenance Phase

Update and manage the knowledge model:

```java
// Update node properties
indexer.updateNode(nodeId, Map.of("status", "inactive"));

// Delete node and edges
indexer.deleteNode(nodeId, cascade = true);

// Reindex after major changes
indexer.fullIndex();
```

## Design Principles

### 1. Domain Agnostic

CEF doesn't prescribe your domain model. You define:
- Node labels (entity types)
- Edge types (relationship types)
- Property schemas

### 2. Flexible Schema

Nodes use JSONB properties for flexibility:
```java
// Medical domain
Map.of("mrn", "12345", "age", 45, "gender", "M")

// E-commerce domain
Map.of("sku", "ABC123", "price", 99.99, "stock", 42)
```

### 3. Semantic Awareness

RelationSemantics guide intelligent traversal:
- **HIERARCHY**: Navigate up/down organizational structures
- **CAUSALITY**: Follow cause-effect chains
- **ASSOCIATION**: Explore peer relationships

### 4. Vectorizable Content

Separate structured properties from semantic content:
- **Properties**: Structured data for filtering/sorting
- **Vectorizable content**: Rich text for semantic search

## Best Practices

### 1. Meaningful Labels

Use descriptive, domain-specific labels:
```java
// Good
"Patient", "Medication", "Doctor"

// Bad
"Entity1", "Thing", "Item"
```

### 2. Rich Vectorizable Content

Provide detailed descriptions for better semantic search:
```java
// Good
"45-year-old male with type 2 diabetes, hypertension, and hyperlipidemia. 
 Currently prescribed Metformin 1000mg BID..."

// Bad
"Patient has diabetes"
```

### 3. Appropriate Granularity

Balance between too fine-grained (noise) and too coarse (loss of detail):
```java
// Too fine-grained
Node bloodPressureReading = new Node(...); // Each reading as separate node

// Too coarse
Node allPatientData = new Node(...); // Everything in one node

// Just right
Node patient = new Node(...);  // Patient entity
Chunk visitNote = new Chunk(...);  // Individual visit notes as chunks
```

### 4. Semantic Relationship Types

Choose relationship types that match your query patterns:
```java
// Medical queries: "Find patients treated by Dr. Smith"
RelationType("TREATS", "Doctor", "Patient", ...)

// E-commerce queries: "Find products in same category"
RelationType("IN_CATEGORY", "Product", "Category", ...)
```

## Next Steps

- Learn about [Indexing](../intro) to populate your knowledge model
- Explore [Retrieval Strategies](../intro) to query your model
- See [Examples](../tutorials/build-your-first-model) for complete implementations
