# Knowledge Model vs. Vector Retrieval Benchmark (Advanced)

**Domain:** Medical (Clinical Decision Support)

**Date:** 2025-11-26T18:55:05.144309047Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).

---

## 1. Network Hop: Patient Zero Link

**Objective:** Find all patients treated by the same doctor as 'PT-10001'.

**Query:** "Find all patients treated by the same doctor as PT-10001"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 43ms | 77ms | ↑79.1% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 77 | +77 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10148
**Provider ID:** DOC-101
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Rheumatoid Arthritis. Reports swelling, joint pain...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10008
**Provider ID:** DOC-102
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Essential Hypertension. Reports headache, noseblee...
```

---

## 2. Intersection: Condition + Medication

**Objective:** Find patients diagnosed with 'Rheumatoid Arthritis' who are also prescribed 'Albuterol'.

**Query:** "Find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 55ms | ↑111.5% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 60 | +60 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10048
**Provider ID:** DOC-102
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Rheumatoid Arthritis. Reports joint pain, stiffnes...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10043
**Provider ID:** DOC-114
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Rheumatoid Arthritis. Reports swelling, stiffness....
```

---

## 3. Aggregation: Provider Pattern

**Objective:** List doctors who are treating more than one patient with 'Rheumatoid Arthritis'.

**Query:** "List doctors who are treating more than one patient with Rheumatoid Arthritis"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 22ms | 67ms | ↑204.5% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 70 | +70 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10046
**Provider ID:** DOC-102
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Rheumatoid Arthritis. Reports joint pain, stiffnes...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10046
**Provider ID:** DOC-102
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Rheumatoid Arthritis. Reports joint pain, stiffnes...
```

---

