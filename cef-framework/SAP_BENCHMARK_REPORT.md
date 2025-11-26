# SAP Knowledge Model Benchmark

**Domain:** Enterprise ERP (Financial & Supply Chain)

**Date:** 2025-11-26T18:14:15.551913090Z

This report demonstrates the Knowledge Model's ability to parse raw SAP table dumps (CSV) and answer complex, temporal queries that Vector Search cannot handle.

---

## Scenario 1: Shadow IT Detection

**Objective:** Analyze the 'Software Subscription' spend trend for the Engineering department over the last 4 quarters. Flag any vendors with increasing costs that do not have a corresponding budget entry.

**Query:** "Analyze spend trend for Engineering department and flag suspicious vendors"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 25ms | 48ms | ↑92.0% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 0 | +0 |
| **Edges Traversed** | 0 | 0 | +0 |
| **Structural Coverage** | ✗ | ✗ | Same |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10109
**Provider ID:** DOC-106
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Congestive Heart Failure. Reports fatigue, dyspnea...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10109
**Provider ID:** DOC-106
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Congestive Heart Failure. Reports fatigue, dyspnea...
```

---

## Scenario 2: Typhoon Impact on Holiday Laptop Delivery

**Objective:** A Typhoon has hit Taiwan for 3 days. Visualize the impact on the 'Holiday Laptop' delivery schedule.

**Query:** "Impact of Typhoon in Taiwan on Holiday Laptop delivery"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 22ms | 42ms | ↑90.9% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 0 | +0 |
| **Edges Traversed** | 0 | 0 | +0 |
| **Structural Coverage** | ✗ | ✗ | Same |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10030
**Provider ID:** DOC-106
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Essential Hypertension. Reports nosebleeds, shortn...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10030
**Provider ID:** DOC-106
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Essential Hypertension. Reports nosebleeds, shortn...
```

---

