# Knowledge Model vs. Vector Retrieval Benchmark (Advanced)

**Domain:** Medical (Clinical Decision Support)

**Date:** 2025-11-26T19:09:03.824369741Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).

---

## 1. Network Hop: Patient Zero Link

**Objective:** Find all patients treated by the same doctor as 'PT-10001'.

**Query:** "Find all patients treated by the same doctor as PT-10001"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 68ms | 93ms | ↑36.8% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
2d629154-642e-4e4c-9946-e36c0ced0917 | node=745b8f8a-119c-4ebf-9f6e-616f771b275f | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10148 **Provider ID:** DOC-101 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
4a370e5a-aa07-4dce-8366-f7d0e0cf42b7 | node=8adddfa7-93cc-4f30-8131-e1e5479be2aa | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10106 **Provider ID:** DOC-110 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
453cb7c4-c695-4b85-a2ca-4845ade0538f | node=04af5628-279a-4968-8345-d3922c112e9e | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10008 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
fd76bd88-cb90-4647-9497-3df213a7a3af | node=9edbd06b-14b3-4471-831f-9cbcfad1e0e1 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10081 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
a8373380-f81b-4141-8725-79f1d8c67c88 | node=270eccaf-c0d8-4936-bd28-e6c2eb3754c6 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10141 **Provider ID:** DOC-111 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
```

**Knowledge Model (Graph RAG):**
```
453cb7c4-c695-4b85-a2ca-4845ade0538f | node=04af5628-279a-4968-8345-d3922c112e9e | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10008 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
fd76bd88-cb90-4647-9497-3df213a7a3af | node=9edbd06b-14b3-4471-831f-9cbcfad1e0e1 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10081 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
80e4487d-4a7b-43d5-910d-f44bf61964f8 | node=b497af41-c991-47e9-8143-61042ed998e3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10054 **Provider ID:** DOC-101 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
5dab8d38-0823-4187-9d35-ed1e6808bdfe | node=dde1198f-4051-4288-b9c9-8c9220c20763 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10002 **Provider ID:** DOC-112 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
d84eebce-6776-4f5b-ba43-5bee44cbda9f | node=d5e94e7d-c097-4dac-a2cd-2904353857dc | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10017 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
```

---

## 2. Intersection: Condition + Medication

**Objective:** Find patients diagnosed with 'Rheumatoid Arthritis' who are also prescribed 'Albuterol'.

**Query:** "Find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 25ms | 52ms | ↑108.0% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
32a48985-2cb0-4159-b7f7-fe8058b24d68 | node=06a38b73-5038-425b-9b6c-ba130c1a5c8d | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10048 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b2295903-b038-418e-b279-7d8efd2d0ee3 | node=39e83fc8-ecc1-4da7-925d-5cfa72bae968 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10043 **Provider ID:** DOC-114 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
2c5f84d5-5c3a-43d8-b05a-fbdecb8b2148 | node=69eda409-b1a4-42c8-a641-5a78a3af454a | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-104 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
fe698dce-c81d-414f-b10c-802f0e20ee80 | node=c01fea75-7070-4c57-a482-e6e6f180fef5 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10014 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
02a5af9c-5a0f-4f0d-9d53-cec7f7cd5679 | node=30bf957c-9c56-4630-adfa-0f7cd87046a7 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10046 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

**Knowledge Model (Graph RAG):**
```
b2295903-b038-418e-b279-7d8efd2d0ee3 | node=39e83fc8-ecc1-4da7-925d-5cfa72bae968 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10043 **Provider ID:** DOC-114 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
32a48985-2cb0-4159-b7f7-fe8058b24d68 | node=06a38b73-5038-425b-9b6c-ba130c1a5c8d | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10048 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
2c5f84d5-5c3a-43d8-b05a-fbdecb8b2148 | node=69eda409-b1a4-42c8-a641-5a78a3af454a | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-104 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
02a5af9c-5a0f-4f0d-9d53-cec7f7cd5679 | node=30bf957c-9c56-4630-adfa-0f7cd87046a7 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10046 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
3bdf93b0-6ded-4b93-aff3-4a19c528f4c8 | node=59f4b11c-0d66-423b-a9ac-2a4eb089314c | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10018 **Provider ID:** DOC-106 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

---

## 3. Aggregation: Provider Pattern

**Objective:** List doctors who are treating more than one patient with 'Rheumatoid Arthritis'.

**Query:** "List doctors who are treating more than one patient with Rheumatoid Arthritis"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 24ms | 66ms | ↑175.0% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
02a5af9c-5a0f-4f0d-9d53-cec7f7cd5679 | node=30bf957c-9c56-4630-adfa-0f7cd87046a7 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10046 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
fe698dce-c81d-414f-b10c-802f0e20ee80 | node=c01fea75-7070-4c57-a482-e6e6f180fef5 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10014 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
2d629154-642e-4e4c-9946-e36c0ced0917 | node=745b8f8a-119c-4ebf-9f6e-616f771b275f | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10148 **Provider ID:** DOC-101 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
d355d8d3-4b0f-44af-9379-382c1ec86577 | node=8100ee33-f04e-470d-bf4e-814104575a69 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10042 **Provider ID:** DOC-107 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
a17b3726-ef91-4682-a5f3-5564d647b734 | node=4a4c1a0b-b2fb-418c-be73-f4f8fea6eb9f | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10016 **Provider ID:** DOC-108 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

**Knowledge Model (Graph RAG):**
```
02a5af9c-5a0f-4f0d-9d53-cec7f7cd5679 | node=30bf957c-9c56-4630-adfa-0f7cd87046a7 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10046 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
fe698dce-c81d-414f-b10c-802f0e20ee80 | node=c01fea75-7070-4c57-a482-e6e6f180fef5 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10014 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
e5b3295e-ac69-4c1c-aa6f-95cf9c8355b4 | node=d4157011-dd4a-4256-9c15-a56cad6b71af | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10087 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
839bd3cc-5ce9-4a5d-9b18-0883fcbd1551 | node=87fbb0b3-afb0-41e0-ac83-9651144907ac | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10092 **Provider ID:** DOC-111 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b2295903-b038-418e-b279-7d8efd2d0ee3 | node=39e83fc8-ecc1-4da7-925d-5cfa72bae968 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10043 **Provider ID:** DOC-114 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

---

