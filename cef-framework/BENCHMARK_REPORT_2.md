# Knowledge Model vs. Vector Retrieval Benchmark (Advanced)
**Domain:** Medical (Clinical Decision Support)
**Date:** Wed Nov 26 16:57:36 BDT 2025

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).

## 1. Network Hop: Patient Zero Link
**Clinical Question:** "Find all patients treated by the same doctor as 'PT-10001'."

### Knowledge Model Output (Graph + Vector)
**Duration:** 37555ms

To find all patients treated by the same doctor as PT-10001, I will first identify the doctor associated with PT-10001 and then retrieve other patients treated by that doctor.

1. I will use the retrieve_context tool to find information about PT-10001 and identify the doctor associated with this patient.
2. Once I have the doctor's information, I will search for other patients treated by the same doctor.

Let's start by retrieving information about PT-1_ten_patient.
<function=retrieve_context>
<parameter=textQuery>
 patients treated by doctor PT-10001
</parameter>
<parameter-graphQuery>{"targets":[{"description":"PT-10001"}]}
</parameter>
</function>
clave

### Vector-Only Output (Baseline RAG)
**Duration:** 33387ms

 To find all patients treated by the same doctor as 'PT-10001', we first need to identify which doctor treated patient 'PT-10001'.

Looking at clinical note #10:

**CLINical Encounter Note**
**Patient ID:** PT-10002
**Provider ID:** DOC-104
**Date:** 2025-11-25

Patient 'PT-10002' was treated by provider 'DOC-104'.

However, patient 'PT-10001' does not appear explicitly listed among the given clinical encounter notes provided aboveschlü


---

## 2. Intersection: Condition + Medication
**Clinical Question:** "Find patients diagnosed with 'Rheumatoid Arthritis' who are also prescribed 'Albuterol'."

### Knowledge Model Output (Graph + Vector)
**Duration:** 52174ms

To find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol, I will perform a targeted search using the knowledge graph. This involves resolving specific entities such as "R rheumatoid arthritis" and " drug albuterol" within the graph. I will then traverse relationships between these entities to identify patients meeting both criteria.

Here’s my plan:
1. Use the ` retrieve_context` tool to search for patients diagnosed with Rheumatoid Arthritis.
2. Similarly, search for patients prescribed Albuterol.
3. Combine these results to identify overlapping patients who meet both criteria.

I'll start by retrieving context for patients diagnosed with Rheumatoid Arthritis.
```json
{"graphQuery":{"targets":[{"description":"Patient diagnosed with Rheumatoid Arthritis"}]}, "textQuery":" patients diagnosed with Rheumatoid Arthritis"}
```
Now I will retrieve context for patients prescribed Albuterol.
```json
{"graphQuery":{"targets":[{" description":"Patient prescribed Albuterol"}]}, "textQuery":" patients prescribed Albuterol"}
```

### Vector-Only Output (Baseline RAG)
**Duration:** 206729ms

