# Knowledge Model vs. Vector Retrieval Benchmark

**Domain:** Medical (Clinical Decision Support)

**Date:** 2025-11-26T19:07:17.080972498Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning.

---

## 1. Safety Check: Contraindicated Medications

**Objective:** Identify patients taking medications that are contraindicated for their specific conditions.

**Query:** "Find patients with conditions taking contraindicated medications"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 55ms | 89ms | ↑61.8% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
d4b7008f-2df0-4adf-83b7-b2ecf2331149 | node=9bc72cfd-cd88-47c0-a055-cb23ae6874c9 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10119 **Provider ID:** DOC-106 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Congestive...
4a370e5a-aa07-4dce-8366-f7d0e0cf42b7 | node=8adddfa7-93cc-4f30-8131-e1e5479be2aa | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10106 **Provider ID:** DOC-110 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
492079c6-ba22-4f6e-b588-a127b58badc4 | node=1cc048f0-cc9a-42de-a4e4-d851b0fb9f54 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10143 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Congestive...
50987c09-5d4c-4f27-bbc7-98e2e2008888 | node=fd749bf7-e012-414b-8813-51440503f7fa | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10147 **Provider ID:** DOC-109 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
0e7303e2-0f86-4862-a3b0-c378beedd754 | node=88ddf718-8c3c-4f13-912c-392ab054f743 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10058 **Provider ID:** DOC-103 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
```

**Knowledge Model (Graph RAG):**
```
50987c09-5d4c-4f27-bbc7-98e2e2008888 | node=fd749bf7-e012-414b-8813-51440503f7fa | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10147 **Provider ID:** DOC-109 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
70df9b44-a8c6-4b50-a73c-8824e00ec603 | node=bfd50ff7-bdcf-4d69-ac1d-346095758b05 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10051 **Provider ID:** DOC-106 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
eab032ba-2026-4136-a90e-89e5f9dc20ed | node=39df2c71-f02d-494f-a3c6-5baf05c7b64a | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10088 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
5dab8d38-0823-4187-9d35-ed1e6808bdfe | node=dde1198f-4051-4288-b9c9-8c9220c20763 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10002 **Provider ID:** DOC-112 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
0b494727-ac41-45f1-a33d-bdbcec5834ee | node=4807b507-4629-48c6-b3c5-4a824804616c | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10108 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Essential ...
```

---

## 2. Behavioral Risk: Smokers with Asthma

**Objective:** Find patients with 'Bronchial Asthma' who are current smokers.

**Query:** "Find patients with Bronchial Asthma who smoke"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 59ms | ↑126.9% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
93d43092-2200-4dce-a360-9e94329ec61a | node=41db90a0-ec91-4814-b54e-e9705fe820db | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10069 **Provider ID:** DOC-112 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
f2b26954-936d-44ec-b650-1960b9e39977 | node=667992ce-ec26-49a4-affd-5b7709f54681 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10142 **Provider ID:** DOC-107 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
cde07798-0f8e-4549-867f-012026e329b9 | node=fa3d9f14-60c6-491a-9f3d-40828a73ad57 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10112 **Provider ID:** DOC-108 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
d2329adc-5894-4efa-9db0-41c480be2f3f | node=4d71dcc0-e2b0-4006-9857-72b73e5f0535 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10117 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
2fd2e73d-7dc2-4a66-aa89-6ddce0312f3b | node=edd310c7-7b68-4777-9441-c7f7fbfdc795 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10039 **Provider ID:** DOC-111 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
```

**Knowledge Model (Graph RAG):**
```
93d43092-2200-4dce-a360-9e94329ec61a | node=41db90a0-ec91-4814-b54e-e9705fe820db | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10069 **Provider ID:** DOC-112 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
f2b26954-936d-44ec-b650-1960b9e39977 | node=667992ce-ec26-49a4-affd-5b7709f54681 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10142 **Provider ID:** DOC-107 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
9f5028d1-bdd6-4ef8-95c9-19db75b989d5 | node=68a976c9-9f8c-43c6-a815-9bcc3bc035d7 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10115 **Provider ID:** DOC-102 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
a4803e9d-0ff1-4d8d-b34f-1b5513322392 | node=fee1015a-d065-4bab-a203-4780854a9cd3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10114 **Provider ID:** DOC-107 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
2093f90c-5722-41a6-90a2-57b57783d183 | node=17387772-8fbc-4e85-af54-63559161f269 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10005 **Provider ID:** DOC-104 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
```

---

## 3. Root Cause Analysis

**Objective:** List side effects reported by patients taking 'Prednisone' who also have 'Type 2 Diabetes'.

**Query:** "Side effects of Prednisone for Diabetes patients"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 29ms | 72ms | ↑148.3% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
570fda6e-327c-4615-a109-77509d08fbe9 | node=540b13f9-e79a-467b-b3d6-196b2f373cc5 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10084 **Provider ID:** DOC-110 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
52143b1e-29b9-4fb9-ac96-267b05550589 | node=595fdc45-f272-4f4e-859d-a26a12a0d654 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10056 **Provider ID:** DOC-114 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
6d437a03-def1-4b7e-827f-81595c416b6d | node=619f8e2d-58e5-4c31-aee5-af134f37190e | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10009 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
d49c063f-1c37-4b85-8194-ac7dc54ea634 | node=3069a7be-e06e-4374-b7f0-a38c724a0c16 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10024 **Provider ID:** DOC-105 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
810fc96e-ec2f-4d08-bc93-2e4bbfee64f7 | node=4ecda377-5f55-4522-bb48-4daa846c754c | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10012 **Provider ID:** DOC-111 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
```

**Knowledge Model (Graph RAG):**
```
e11f9a6d-485c-4721-b660-40c774dfe207 | node=13364e60-4011-445c-b984-fc834890a457 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10028 **Provider ID:** DOC-113 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
d49c063f-1c37-4b85-8194-ac7dc54ea634 | node=3069a7be-e06e-4374-b7f0-a38c724a0c16 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10024 **Provider ID:** DOC-105 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
323de16b-749d-442f-a6f9-4344e26c0d8c | node=e0cad4e1-bbaa-4408-b5cd-a88d368ee38b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10089 **Provider ID:** DOC-105 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
6d437a03-def1-4b7e-827f-81595c416b6d | node=619f8e2d-58e5-4c31-aee5-af134f37190e | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10009 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
344cfecb-03d3-484a-ad5f-5951bdcedca0 | node=7cb07a04-3825-4617-ab08-5673e221a2a3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10093 **Provider ID:** DOC-100 **Date:** 2025-11-26  **SUBJECTIVE:** Patient presents for follow-up of Type 2 Dia...
```

---

