# Knowledge Model vs. Vector Retrieval Benchmark

**Domain:** Medical (Clinical Decision Support)

**Date:** 2025-11-26T18:57:41.864543108Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning.

---

## 1. Safety Check: Contraindicated Medications

**Objective:** Identify patients taking medications that are contraindicated for their specific conditions.

**Query:** "Find patients with conditions taking contraindicated medications"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 56ms | 83ms | ↑48.2% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 84 | +84 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10119
**Provider ID:** DOC-106
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Congestive Heart Failure. Reports dyspnea, edema. ...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10147
**Provider ID:** DOC-109
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Essential Hypertension. Reports headache, shortnes...
```

---

## 2. Behavioral Risk: Smokers with Asthma

**Objective:** Find patients with 'Bronchial Asthma' who are current smokers.

**Query:** "Find patients with Bronchial Asthma who smoke"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 48ms | ↑84.6% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 75 | +75 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10069
**Provider ID:** DOC-112
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Bronchial Asthma. Reports chest tightness, coughin...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10069
**Provider ID:** DOC-112
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Bronchial Asthma. Reports chest tightness, coughin...
```

---

## 3. Root Cause Analysis

**Objective:** List side effects reported by patients taking 'Prednisone' who also have 'Type 2 Diabetes'.

**Query:** "Side effects of Prednisone for Diabetes patients"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 54ms | ↑107.7% |
| **Chunks Retrieved** | 5 | 5 | - |
| **Nodes Retrieved** | 0 | 50 | +50 |
| **Edges Traversed** | 0 | 54 | +54 |
| **Structural Coverage** | ✗ | ✓ | Better |

### Analysis

**Context Sample (Vector-Only):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10084
**Provider ID:** DOC-110
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Type 2 Diabetes Mellitus. Reports blurred vision, ...
```

**Context Sample (Knowledge Model):**
```
**CLINICAL ENCOUNTER NOTE**
**Patient ID:** PT-10089
**Provider ID:** DOC-105
**Date:** 2025-11-26

**SUBJECTIVE:**
Patient presents for follow-up of Type 2 Diabetes Mellitus. Reports fatigue, polydip...
```

---

