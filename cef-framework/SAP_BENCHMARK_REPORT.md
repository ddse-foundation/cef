# Knowledge Model vs. Vector Retrieval Benchmark (SAP)

**Domain:** Enterprise ERP (Financial & Supply Chain)

**Date:** 2025-11-27T07:52:36.090424431Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on SAP ERP scenarios that require temporal reasoning and supply-chain awareness.

---

## 1. Cross-Project Resource Allocation

**Objective:** Find all cost centers and departments involved in funding overrun projects (requires Project[STATUS=OVERRUN]→FUNDED_BY→Department→HAS_COST_CENTER→CostCenter to discover organizational funding structure and budget exposure across Engineering).

**Query:** "Find all cost centers funding projects with budget overruns"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 51ms | 56ms | ↑9.8% |
| **Chunks Retrieved** | 5 | 8 | +3 |

### Raw Results

**Vector-Only (Naive RAG):**
```
23648d1c-7f57-3455-9371-01b0e67d83c5 | node=dbdf0e07-3f96-3ed9-b4c4-05e086482c33 | **DEPARTMENT** Department ID: OPS Name: Operations Cost Overrun Status: On budget
61cd1d07-3218-34b3-8679-fd6eed64f41a | node=1fd01c54-d076-3c0d-8d3f-4d138a5bb11e | **DEPARTMENT** Department ID: MKT Name: Marketing Cost Overrun Status: On budget
dea5899a-2545-3b48-97f6-a87d0e9f565e | node=34d03520-8b75-332c-9b78-ef575c92ecf7 | **DEPARTMENT** Department ID: ENG Name: Engineering Cost Overrun Status: Has overruns
a0c21933-b331-3172-a86a-bbb67a403c30 | node=d5af2c4d-150c-3889-996e-8d9dda7f68c8 | **DEPARTMENT** Department ID: HR Name: Human Resources Cost Overrun Status: On budget
6f007418-c08c-3d5c-a136-90243a1840fe | node=6de9e72d-8500-331d-b7c4-ba774b74b9fd | **PROJECT** Project ID: PROJ-001 Name: Cloud Migration Phase 2 Department: ENG Status: OVERRUN
```

**Knowledge Model (Graph RAG):**
```
9d06e196-93aa-392e-ab99-4c47c5dc07c1 | node=ca42f96c-e59e-34f0-a970-d633a937b430 | **COST CENTER PROFILE** Cost Center: CC-102 Name: Data Science Department: ENG
a0c21933-b331-3172-a86a-bbb67a403c30 | node=d5af2c4d-150c-3889-996e-8d9dda7f68c8 | **DEPARTMENT** Department ID: HR Name: Human Resources Cost Overrun Status: On budget
dea5899a-2545-3b48-97f6-a87d0e9f565e | node=34d03520-8b75-332c-9b78-ef575c92ecf7 | **DEPARTMENT** Department ID: ENG Name: Engineering Cost Overrun Status: Has overruns
61cd1d07-3218-34b3-8679-fd6eed64f41a | node=1fd01c54-d076-3c0d-8d3f-4d138a5bb11e | **DEPARTMENT** Department ID: MKT Name: Marketing Cost Overrun Status: On budget
bd509c7a-2e38-3516-9027-46f8e27ba430 | node=45f6c926-9969-3e6c-b20a-0f3465400d8b | **COST CENTER PROFILE** Cost Center: CC-200 Name: Marketing US Department: MKT
447f1ad0-94ff-382b-8711-fa8c1cdfa809 | node=dd251da6-d0fa-3f7a-aaa7-c840742bd3b5 | **COST CENTER PROFILE** Cost Center: CC-101 Name: DevOps Department: ENG
1f1d053b-19b0-3798-8e86-d00c01f0a206 | node=b53ea3c6-bd23-30c9-88cf-4c84803a4877 | **COST CENTER PROFILE** Cost Center: CC-100 Name: Engineering Core Department: ENG
8f1e0928-744d-33c9-96bb-d5f352682529 | node=d0e72c85-28e2-3e0a-8712-34061d04b1ee | **COST CENTER PROFILE** Cost Center: CC-300 Name: HR Global Department: HR
```

---

## 2. Cost Center Contagion Analysis

**Objective:** Find cost centers at risk due to shared vendor dependencies: Department1→CostCenter1→Vendor→Component→UsedBy→Project→FundedBy→Department2→HasOverrun (requires detecting risk propagation across department boundaries via shared vendors).

**Query:** "Find departments at financial risk via shared vendor dependencies with overrun departments"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 18ms | 29ms | ↑61.1% |
| **Chunks Retrieved** | 5 | 8 | +3 |

### Raw Results

**Vector-Only (Naive RAG):**
```
dea5899a-2545-3b48-97f6-a87d0e9f565e | node=34d03520-8b75-332c-9b78-ef575c92ecf7 | **DEPARTMENT** Department ID: ENG Name: Engineering Cost Overrun Status: Has overruns
23648d1c-7f57-3455-9371-01b0e67d83c5 | node=dbdf0e07-3f96-3ed9-b4c4-05e086482c33 | **DEPARTMENT** Department ID: OPS Name: Operations Cost Overrun Status: On budget
61cd1d07-3218-34b3-8679-fd6eed64f41a | node=1fd01c54-d076-3c0d-8d3f-4d138a5bb11e | **DEPARTMENT** Department ID: MKT Name: Marketing Cost Overrun Status: On budget
a0c21933-b331-3172-a86a-bbb67a403c30 | node=d5af2c4d-150c-3889-996e-8d9dda7f68c8 | **DEPARTMENT** Department ID: HR Name: Human Resources Cost Overrun Status: On budget
62d3c329-aaec-3b68-9e55-1ba6e14bee74 | node=d5b07780-19cf-3ab1-8ea1-d02d8f2d4db6 | Invoice 100014 posted on 2025-02-10 captured 99.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
```

**Knowledge Model (Graph RAG):**
```
9d06e196-93aa-392e-ab99-4c47c5dc07c1 | node=ca42f96c-e59e-34f0-a970-d633a937b430 | **COST CENTER PROFILE** Cost Center: CC-102 Name: Data Science Department: ENG
1f1d053b-19b0-3798-8e86-d00c01f0a206 | node=b53ea3c6-bd23-30c9-88cf-4c84803a4877 | **COST CENTER PROFILE** Cost Center: CC-100 Name: Engineering Core Department: ENG
dea5899a-2545-3b48-97f6-a87d0e9f565e | node=34d03520-8b75-332c-9b78-ef575c92ecf7 | **DEPARTMENT** Department ID: ENG Name: Engineering Cost Overrun Status: Has overruns
bd509c7a-2e38-3516-9027-46f8e27ba430 | node=45f6c926-9969-3e6c-b20a-0f3465400d8b | **COST CENTER PROFILE** Cost Center: CC-200 Name: Marketing US Department: MKT
8f1e0928-744d-33c9-96bb-d5f352682529 | node=d0e72c85-28e2-3e0a-8712-34061d04b1ee | **COST CENTER PROFILE** Cost Center: CC-300 Name: HR Global Department: HR
447f1ad0-94ff-382b-8711-fa8c1cdfa809 | node=dd251da6-d0fa-3f7a-aaa7-c840742bd3b5 | **COST CENTER PROFILE** Cost Center: CC-101 Name: DevOps Department: ENG
61cd1d07-3218-34b3-8679-fd6eed64f41a | node=1fd01c54-d076-3c0d-8d3f-4d138a5bb11e | **DEPARTMENT** Department ID: MKT Name: Marketing Cost Overrun Status: On budget
a0c21933-b331-3172-a86a-bbb67a403c30 | node=d5af2c4d-150c-3889-996e-8d9dda7f68c8 | **DEPARTMENT** Department ID: HR Name: Human Resources Cost Overrun Status: On budget
```

---

