# Knowledge Model vs. Vector Retrieval Benchmark (SAP)

**Domain:** Enterprise ERP (Financial & Supply Chain)

**Date:** 2025-11-26T19:22:41.935573032Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on SAP ERP scenarios that require temporal reasoning and supply-chain awareness.

---

## 1. Shadow IT Detection

**Objective:** Analyze the 'Software Subscription' spend trend for the Engineering department over the last 4 quarters. Flag any vendors with increasing costs that do not have a corresponding budget entry.

**Query:** "Analyze spend trend for Engineering department and flag suspicious vendors"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 51ms | 97ms | ↑90.2% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
be050910-13f0-3339-8569-eea075301389 | node=8f73667e-92e5-3362-a83d-778e65389fe1 | Invoice 100017 posted on 2025-03-27 captured 253.60 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
326a13d6-40de-3587-94cf-baccdf7c944a | node=692de3c1-8d26-38ee-b89b-d1ffc60d63ad | Invoice 100012 posted on 2025-02-10 captured 99.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
30655d7d-be9a-3d6e-a4bb-90bd24192a91 | node=d669aa2b-c63d-3e39-ae3c-7aab09647c86 | Invoice 100011 posted on 2025-01-16 captured 49.00 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's softw...
863f8acf-8126-3df4-9218-ff6bdad3d8ef | node=9e3d9c9c-2348-3d3d-a5ed-65478b9b340c | Invoice 100016 posted on 2025-03-23 captured 248.87 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
f5230f00-a543-37f7-9eb7-d16333eceb31 | node=160728c6-c6cf-3364-b01a-d020150ca158 | Invoice 100010 posted on 2025-01-11 captured 49.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
```

**Knowledge Model (Graph RAG):**
```
b9c61b31-7208-3074-86d5-ac3569b3b248 | node=c384cee5-c4a2-3eb4-a203-ea3ab6865c00 | Invoice 100002 posted on 2025-01-27 captured 156.97 USD against cost center Data Science (department: ENG) associated with the Engineering department's software...
92a743b6-429c-34da-b86c-c7b5e3cc0ce7 | node=a5dfe2fc-b9f4-309a-bea2-79e43efb31c4 | Invoice 100006 posted on 2025-03-05 captured 127.04 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
3fff4594-c7a3-31af-a93d-549c96650ac8 | node=6b1353b4-3890-3021-a7e3-96a65c9a0caf | Invoice 100009 posted on 2025-03-23 captured 115.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
db69e465-41b4-383d-b7a1-074f53e9c38e | node=726fc6f8-b305-386a-a2f7-1a9686292904 | Invoice 100001 posted on 2025-01-12 captured 191.95 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
be050910-13f0-3339-8569-eea075301389 | node=8f73667e-92e5-3362-a83d-778e65389fe1 | Invoice 100017 posted on 2025-03-27 captured 253.60 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
```

---

## 2. Typhoon Impact: Holiday Laptop Delivery

**Objective:** A Typhoon has hit Taiwan for 3 days. Visualize the impact on the 'Holiday Laptop' delivery schedule.

**Query:** "Impact of Typhoon in Taiwan on Holiday Laptop delivery"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 54ms | ↑107.7% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
b9c61b31-7208-3074-86d5-ac3569b3b248 | node=c384cee5-c4a2-3eb4-a203-ea3ab6865c00 | Invoice 100002 posted on 2025-01-27 captured 156.97 USD against cost center Data Science (department: ENG) associated with the Engineering department's software...
3fff4594-c7a3-31af-a93d-549c96650ac8 | node=6b1353b4-3890-3021-a7e3-96a65c9a0caf | Invoice 100009 posted on 2025-03-23 captured 115.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
db69e465-41b4-383d-b7a1-074f53e9c38e | node=726fc6f8-b305-386a-a2f7-1a9686292904 | Invoice 100001 posted on 2025-01-12 captured 191.95 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
```

**Knowledge Model (Graph RAG):**
```
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
b9c61b31-7208-3074-86d5-ac3569b3b248 | node=c384cee5-c4a2-3eb4-a203-ea3ab6865c00 | Invoice 100002 posted on 2025-01-27 captured 156.97 USD against cost center Data Science (department: ENG) associated with the Engineering department's software...
8b952571-644b-31af-95a5-e286aabfc79c | node=3bab9611-f209-35eb-b518-788cc1c59f53 | Invoice 100004 posted on 2025-02-12 captured 147.69 USD against cost center Marketing US (department: MKT) in the MKT organization. Vendor recorded: Staples. Po...
92a743b6-429c-34da-b86c-c7b5e3cc0ce7 | node=a5dfe2fc-b9f4-309a-bea2-79e43efb31c4 | Invoice 100006 posted on 2025-03-05 captured 127.04 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
```

---

