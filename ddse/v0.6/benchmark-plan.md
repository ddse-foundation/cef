
## Complete Benchmark Reorganization Plan (Aligned with Python Scripts)

### Current State Analysis

| Component | Current | Problem |
|-----------|---------|---------|
| **Java Tests** | `MedicalBenchmarkTest`, `MedicalBenchmarkTest2`, `SapBenchmarkTest` | Duplicate data loading, single backend |
| **Python Eval** | evaluate_benchmarks.py, evaluate_benchmark_sap.py | Hardcoded data, separate scripts |
| **Python Viz** | visualize_results.py | Only compares Vector vs KM, not backends |
| **Data Gen** | generate_medical_data.py, `generate_sap_data.py` | Good, keep as-is |

### Unified Architecture

```
src/test/
├── java/org/ddse/ml/cef/benchmark/
│   ├── core/                              # Shared infrastructure
│   │   ├── BenchmarkDataCache.java        # Singleton: load once, reuse
│   │   ├── BenchmarkConfig.java           # Config: iterations, warmup, etc.
│   │   ├── BenchmarkResult.java           # DTO: metrics per scenario
│   │   └── BenchmarkResultWriter.java     # JSON output for Python scripts
│   ├── dataset/
│   │   ├── DatasetLoader.java             # Parse JSON, create nodes/edges
│   │   ├── MedicalDataset.java            # Medical domain holder
│   │   └── SapDataset.java                # SAP domain holder
│   ├── scenario/                          # Domain-specific benchmarks
│   │   ├── MedicalScenarios.java          # 4 medical scenarios (contraindication, etc.)
│   │   └── SapScenarios.java              # 2 SAP scenarios (resource alloc, contagion)
│   └── runner/                            # Per-backend test classes
│       ├── InMemoryBenchmarkIT.java       # JGraphT baseline
│       ├── Neo4jBenchmarkIT.java          # Neo4j via Testcontainers
│       ├── PgSqlBenchmarkIT.java          # Pure PostgreSQL SQL
│       └── PgAgeBenchmarkIT.java          # PostgreSQL + Apache AGE
│
└── resources/scripts/
    ├── generate_medical_data.py           # (existing) - keep
    ├── generate_sap_data.py               # (existing) - keep
    ├── evaluate_all_backends.py           # NEW: unified evaluation
    ├── visualize_backend_comparison.py    # NEW: cross-backend charts
    └── results/
        ├── inmemory/
        │   ├── medical_results.json       # Output from Java tests
        │   └── sap_results.json
        ├── neo4j/
        │   ├── medical_results.json
        │   └── sap_results.json
        ├── pgsql/
        │   ├── medical_results.json
        │   └── sap_results.json
        ├── pgage/
        │   ├── medical_results.json
        │   └── sap_results.json
        ├── BACKEND_COMPARISON.md          # Auto-generated summary
        └── charts/
            ├── traversal_latency_comparison.png
            ├── context_assembly_comparison.png
            └── backend_recommendation_matrix.png
```

### JSON Output Format (Java → Python)

```json
{
  "backend": "neo4j",
  "dataset": "medical",
  "timestamp": "2025-12-07T14:30:00Z",
  "datasetStats": {
    "nodes": 1247,
    "edges": 3891,
    "labels": ["Patient", "Condition", "Medication", "Doctor"]
  },
  "scenarios": [
    {
      "name": "Multi-Hop Contraindication Discovery",
      "vectorOnlyChunks": 5,
      "knowledgeModelChunks": 12,
      "vectorLatencyMs": {"p50": 22, "p95": 34, "p99": 45},
      "kmLatencyMs": {"p50": 23, "p95": 38, "p99": 52},
      "graphNodesTraversed": 8,
      "patternsExecuted": ["Patient→HAS_CONDITION→*", "Patient→PRESCRIBED_MEDICATION→*"]
    }
  ],
  "summary": {
    "avgChunkImprovement": 95.0,
    "avgLatencyOverhead": 19.5,
    "totalBenchmarkTimeMs": 4523
  }
}
```

### Enhanced Python Scripts

#### `evaluate_all_backends.py` (NEW)
```python
#!/usr/bin/env python3
"""
Unified benchmark evaluation across all graph store backends.
Reads JSON results from Java tests, computes ground truth accuracy,
generates comparison tables.
"""

BACKENDS = ['inmemory', 'neo4j', 'pgsql', 'pgage']
DATASETS = ['medical', 'sap']

def load_results(backend, dataset):
    path = f"results/{backend}/{dataset}_results.json"
    ...

def compute_ground_truth_accuracy(results, dataset):
    # Reuse logic from existing evaluate_benchmarks.py
    ...

def generate_comparison_table():
    """Generate Markdown table comparing all backends"""
    ...

def main():
    for dataset in DATASETS:
        for backend in BACKENDS:
            results = load_results(backend, dataset)
            accuracy = compute_ground_truth_accuracy(results, dataset)
            ...
    generate_comparison_table()
```

#### `visualize_backend_comparison.py` (NEW)
```python
#!/usr/bin/env python3
"""
Generate cross-backend comparison visualizations.
"""

def plot_traversal_latency_comparison():
    """4-bar chart: InMemory vs Neo4j vs PgSql vs PgAge"""
    ...

def plot_context_assembly_heatmap():
    """Heatmap: Scenarios x Backends with latency colors"""
    ...

def plot_recommendation_matrix():
    """Decision matrix: Use case → Recommended backend"""
    ...

def main():
    plot_traversal_latency_comparison()
    plot_context_assembly_heatmap()
    plot_recommendation_matrix()
    generate_summary_report()
```

### Expected Output: BACKEND_COMPARISON.md

```markdown
# CEF v0.6 Backend Comparison Report
Generated: 2025-12-07

## Medical Dataset (1,247 nodes, 3,891 edges)

### Scenario 1: Multi-Hop Contraindication Discovery

| Backend | KM Chunks | p50 Latency | p95 Latency | Graph Nodes |
|---------|-----------|-------------|-------------|-------------|
| InMemory | 12 | 23ms | 38ms | 8 |
| Neo4j | 12 | 45ms | 89ms | 8 |
| PgSql | 12 | 78ms | 156ms | 8 |
| PgAge | 12 | 52ms | 98ms | 8 |

### Performance Summary

| Backend | Avg Latency | Relative to InMemory | Recommended For |
|---------|-------------|---------------------|-----------------|
| InMemory | 26ms | 1.0x (baseline) | Dev, testing, <100K nodes |
| Neo4j | 67ms | 2.6x | Production, complex traversals |
| PgAge | 73ms | 2.8x | Unified PostgreSQL deployments |
| PgSql | 112ms | 4.3x | Simple queries, max compatibility |

## Visualization

![Traversal Latency](charts/traversal_latency_comparison.png)
![Backend Recommendation](charts/backend_recommendation_matrix.png)
```

### Execution Flow

```bash
# Step 1: Generate test data (if needed)
cd src/test/resources/scripts
python generate_medical_data.py
python generate_sap_data.py

# Step 2: Run Java benchmarks (outputs JSON to results/)
cd ../../../../
mvn verify -Pbenchmark  # Runs all *BenchmarkIT tests

# Step 3: Evaluate and visualize
cd src/test/resources/scripts
python evaluate_all_backends.py      # Computes accuracy, generates BACKEND_COMPARISON.md
python visualize_backend_comparison.py  # Creates PNG charts

# Or run everything with one script:
./run_full_benchmark.sh
```

### Migration Plan

| Step | Task | Files Changed |
|------|------|---------------|
| 1 | Create `BenchmarkDataCache` singleton | New file |
| 2 | Create `BenchmarkResultWriter` for JSON output | New file |
| 3 | Refactor `MedicalBenchmarkTest` → `MedicalScenarios` | Modify existing |
| 4 | Refactor `SapBenchmarkTest` → `SapScenarios` | Modify existing |
| 5 | Create 4 runner classes (InMemory, Neo4j, PgSql, PgAge) | New files |
| 6 | Create `evaluate_all_backends.py` | New file |
| 7 | Create `visualize_backend_comparison.py` | New file |
| 8 | Update existing viz scripts to call new unified one | Modify existing |
| 9 | Create `run_full_benchmark.sh` orchestration | New file |

---

