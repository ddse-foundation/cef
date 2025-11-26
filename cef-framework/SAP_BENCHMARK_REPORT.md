# SAP Knowledge Model Benchmark
**Domain:** Enterprise ERP (Financial & Supply Chain)
**Date:** Wed Nov 26 01:24:00 BDT 2025

This report demonstrates the Knowledge Model's ability to parse raw SAP table dumps (CSV) and answer complex, temporal queries that Vector Search cannot handle.

## 1. Financial GL Analyzer: Shadow IT Detection
**User Query:** "Analyze the 'Software Subscription' spend trend for the Engineering department over the last 4 quarters. Flag any vendors with increasing costs that do not have a corresponding budget entry."

### Vector Only
**Start Time:** Wed Nov 26 01:24:00 BDT 2025
**Execution Time:** 161 ms
```text
# Context Retrieval Result

**Strategy:** VECTOR_ONLY
**Results:** 0 chunks

## Vector Context

```

### Knowledge Model
**Start Time:** Wed Nov 26 01:24:10 BDT 2025
**Execution Time:** 868 ms
```text
# Context Retrieval Result

**Strategy:** GRAPH_ONLY
**Retrieval Time:** 866ms
**Results:** 0 nodes, 0 edges, 0 chunks

*No context found for the given query.*

```

## 2. Supply Chain: Butterfly Effect Analysis
**User Query:** "A Typhoon has hit Taiwan for 3 days. Visualize the impact on the 'Holiday Laptop' delivery schedule."

### Vector Only
**Start Time:** Wed Nov 26 01:24:11 BDT 2025
**Execution Time:** 28 ms
```text
# Context Retrieval Result

**Strategy:** VECTOR_ONLY
**Results:** 0 chunks

## Vector Context

```

### Knowledge Model
**Start Time:** Wed Nov 26 01:24:19 BDT 2025
**Execution Time:** 14 ms
```text
# Context Retrieval Result

**Strategy:** GRAPH_ONLY
**Retrieval Time:** 14ms
**Results:** 0 nodes, 0 edges, 0 chunks

*No context found for the given query.*

```

