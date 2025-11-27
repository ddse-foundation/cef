# Knowledge Model vs. Vector Retrieval Benchmark (SAP)

**Domain:** Enterprise ERP (Financial & Supply Chain)

**Date:** 2025-11-27T04:02:02.457036052Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on SAP ERP scenarios that require temporal reasoning and supply-chain awareness.

---

## 1. Shadow IT Budget Leak Detection

**Objective:** Find invoices paid to vendors NOT in approved budget, where the vendor supplies components used by Engineering, AND those components are used in projects with cost overruns (requires: Invoice→Vendor→Component→Project→Department→Budget→ApprovedVendors).

**Query:** "Find Engineering invoices to unapproved vendors supplying overrun projects"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 20ms | 38ms | ↑90.0% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
3f0d563d-c0b4-3db3-a366-8e3d5be243c4 | node=68c8b28b-f34f-30a5-a122-5d1402d3552f | Invoice 100014 posted on 2025-02-15 captured 150.00 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
02511ac6-1d3a-3b41-9692-b07d48f9c804 | node=6ae985b2-da0a-3e7f-a21c-cb0d014c2bd0 | Invoice 100018 posted on 2025-03-16 captured 211.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
be050910-13f0-3339-8569-eea075301389 | node=8f73667e-92e5-3362-a83d-778e65389fe1 | Invoice 100017 posted on 2025-03-27 captured 253.60 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
30655d7d-be9a-3d6e-a4bb-90bd24192a91 | node=d669aa2b-c63d-3e39-ae3c-7aab09647c86 | Invoice 100011 posted on 2025-01-16 captured 49.00 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's softw...
863f8acf-8126-3df4-9218-ff6bdad3d8ef | node=9e3d9c9c-2348-3d3d-a5ed-65478b9b340c | Invoice 100016 posted on 2025-03-23 captured 248.87 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
```

**Knowledge Model (Graph RAG):**
```
3f0d563d-c0b4-3db3-a366-8e3d5be243c4 | node=68c8b28b-f34f-30a5-a122-5d1402d3552f | Invoice 100014 posted on 2025-02-15 captured 150.00 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
02511ac6-1d3a-3b41-9692-b07d48f9c804 | node=6ae985b2-da0a-3e7f-a21c-cb0d014c2bd0 | Invoice 100018 posted on 2025-03-16 captured 211.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
be050910-13f0-3339-8569-eea075301389 | node=8f73667e-92e5-3362-a83d-778e65389fe1 | Invoice 100017 posted on 2025-03-27 captured 253.60 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
30655d7d-be9a-3d6e-a4bb-90bd24192a91 | node=d669aa2b-c63d-3e39-ae3c-7aab09647c86 | Invoice 100011 posted on 2025-01-16 captured 49.00 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's softw...
863f8acf-8126-3df4-9218-ff6bdad3d8ef | node=9e3d9c9c-2348-3d3d-a5ed-65478b9b340c | Invoice 100016 posted on 2025-03-23 captured 248.87 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's soft...
```

---

## 2. Transitive Supply Chain Disruption

**Objective:** Find ALL products affected by Taiwan typhoon through multi-tier supply chain: Typhoon→Location→Vendor→Component→SubAssembly→Product→CustomerOrder (requires 6-hop traversal to find downstream impact).

**Query:** "Find all customer orders affected by Taiwan typhoon via supply chain cascade"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 19ms | 40ms | ↑110.5% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
8e9e08a3-739b-3071-b333-b3245158b700 | node=abe8d9da-cf8c-30a8-93a1-1407624612a1 | **VENDOR MASTER DATA** Vendor ID: V-2001 Name: Taiwan Semi (TSMC) City: Hsinchu Country: TW
8b952571-644b-31af-95a5-e286aabfc79c | node=3bab9611-f209-35eb-b518-788cc1c59f53 | Invoice 100004 posted on 2025-02-12 captured 147.69 USD against cost center Marketing US (department: MKT) in the MKT organization. Vendor recorded: Staples. Po...
b9c61b31-7208-3074-86d5-ac3569b3b248 | node=c384cee5-c4a2-3eb4-a203-ea3ab6865c00 | Invoice 100002 posted on 2025-01-27 captured 156.97 USD against cost center Data Science (department: ENG) associated with the Engineering department's software...
```

**Knowledge Model (Graph RAG):**
```
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
8e9e08a3-739b-3071-b333-b3245158b700 | node=abe8d9da-cf8c-30a8-93a1-1407624612a1 | **VENDOR MASTER DATA** Vendor ID: V-2001 Name: Taiwan Semi (TSMC) City: Hsinchu Country: TW
8b952571-644b-31af-95a5-e286aabfc79c | node=3bab9611-f209-35eb-b518-788cc1c59f53 | Invoice 100004 posted on 2025-02-12 captured 147.69 USD against cost center Marketing US (department: MKT) in the MKT organization. Vendor recorded: Staples. Po...
b9c61b31-7208-3074-86d5-ac3569b3b248 | node=c384cee5-c4a2-3eb4-a203-ea3ab6865c00 | Invoice 100002 posted on 2025-01-27 captured 156.97 USD against cost center Data Science (department: ENG) associated with the Engineering department's software...
```

---

## 3. Transitive Vendor Single Point of Failure

**Objective:** Identify products dependent on Taiwan Semi through hidden chains: Product→Component→Subcomponent→RawMaterial→Supplier→Vendor, where Vendor=TSMC but product documentation only lists Component (requires 5-hop to discover hidden dependency).

**Query:** "Find products with hidden transitive dependencies on Taiwan Semi"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 18ms | 37ms | ↑105.6% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
8e9e08a3-739b-3071-b333-b3245158b700 | node=abe8d9da-cf8c-30a8-93a1-1407624612a1 | **VENDOR MASTER DATA** Vendor ID: V-2001 Name: Taiwan Semi (TSMC) City: Hsinchu Country: TW
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
30a4a364-2632-3f09-8481-c69da5c2ed89 | node=b64d6cda-fd1d-38c9-931d-0a97fcecf50d | **VENDOR MASTER DATA** Vendor ID: V-2002 Name: Samsung Display City: Seoul Country: KR
ddee4ede-f11c-3a41-a8ca-6d5f2466726a | node=8a0cb650-ac5b-3460-a0ed-df14887e8e34 | **VENDOR MASTER DATA** Vendor ID: V-2003 Name: LG Chem City: Seoul Country: KR
```

**Knowledge Model (Graph RAG):**
```
8e9e08a3-739b-3071-b333-b3245158b700 | node=abe8d9da-cf8c-30a8-93a1-1407624612a1 | **VENDOR MASTER DATA** Vendor ID: V-2001 Name: Taiwan Semi (TSMC) City: Hsinchu Country: TW
2d6dc02e-94a5-3c1e-b475-589d6cfa54db | node=694259e5-9be6-3f21-aa12-9d94839b2369 | Super Typhoon Kong-rey stalled shipments in Taiwan and directly impacted Taiwan Semi (TSMC) in Hsinchu, TW. Holiday Laptop Pro components (CPU Ryzen 9 and GPU R...
185c7740-7ff4-3695-a368-eb932d465d11 | node=bf7b338d-de21-3664-8d1f-e26bada04407 | Taiwan Semi (TSMC) Hsinchu Lead Time: 14 days. Typhoon disruptions in Taiwan extend the Holiday Laptop delivery queue. Holiday Laptop Pro requires components: O...
30a4a364-2632-3f09-8481-c69da5c2ed89 | node=b64d6cda-fd1d-38c9-931d-0a97fcecf50d | **VENDOR MASTER DATA** Vendor ID: V-2002 Name: Samsung Display City: Seoul Country: KR
ddee4ede-f11c-3a41-a8ca-6d5f2466726a | node=8a0cb650-ac5b-3460-a0ed-df14887e8e34 | **VENDOR MASTER DATA** Vendor ID: V-2003 Name: LG Chem City: Seoul Country: KR
```

---

## 4. Cost Center Contagion Analysis

**Objective:** Find cost centers at risk due to shared vendor dependencies: Department1→CostCenter1→Vendor→Component→UsedBy→Project→FundedBy→Department2→HasOverrun (requires detecting risk propagation across department boundaries via shared vendors).

**Query:** "Find departments at financial risk via shared vendor dependencies with overrun departments"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 20ms | 35ms | ↑75.0% |
| **Chunks Retrieved** | 5 | 5 | - |

### Raw Results

**Vector-Only (Naive RAG):**
```
f5230f00-a543-37f7-9eb7-d16333eceb31 | node=160728c6-c6cf-3364-b01a-d020150ca158 | Invoice 100010 posted on 2025-01-11 captured 49.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
326a13d6-40de-3587-94cf-baccdf7c944a | node=692de3c1-8d26-38ee-b89b-d1ffc60d63ad | Invoice 100012 posted on 2025-02-10 captured 99.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
30655d7d-be9a-3d6e-a4bb-90bd24192a91 | node=d669aa2b-c63d-3e39-ae3c-7aab09647c86 | Invoice 100011 posted on 2025-01-16 captured 49.00 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's softw...
02511ac6-1d3a-3b41-9692-b07d48f9c804 | node=6ae985b2-da0a-3e7f-a21c-cb0d014c2bd0 | Invoice 100018 posted on 2025-03-16 captured 211.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
3f0d563d-c0b4-3db3-a366-8e3d5be243c4 | node=68c8b28b-f34f-30a5-a122-5d1402d3552f | Invoice 100014 posted on 2025-02-15 captured 150.00 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
```

**Knowledge Model (Graph RAG):**
```
f5230f00-a543-37f7-9eb7-d16333eceb31 | node=160728c6-c6cf-3364-b01a-d020150ca158 | Invoice 100010 posted on 2025-01-11 captured 49.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
326a13d6-40de-3587-94cf-baccdf7c944a | node=692de3c1-8d26-38ee-b89b-d1ffc60d63ad | Invoice 100012 posted on 2025-02-10 captured 99.00 USD against cost center Data Science (department: ENG) associated with the Engineering department's software ...
30655d7d-be9a-3d6e-a4bb-90bd24192a91 | node=d669aa2b-c63d-3e39-ae3c-7aab09647c86 | Invoice 100011 posted on 2025-01-16 captured 49.00 USD against cost center Engineering Core (department: ENG) associated with the Engineering department's softw...
02511ac6-1d3a-3b41-9692-b07d48f9c804 | node=6ae985b2-da0a-3e7f-a21c-cb0d014c2bd0 | Invoice 100018 posted on 2025-03-16 captured 211.14 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
3f0d563d-c0b4-3db3-a366-8e3d5be243c4 | node=68c8b28b-f34f-30a5-a122-5d1402d3552f | Invoice 100014 posted on 2025-02-15 captured 150.00 USD against cost center DevOps (department: ENG) associated with the Engineering department's software subsc...
```

---

