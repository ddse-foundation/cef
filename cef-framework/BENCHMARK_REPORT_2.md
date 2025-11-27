# Knowledge Model vs. Vector Retrieval Benchmark (Advanced)

**Domain:** Medical (Clinical Decision Support)

**Date:** 2025-11-27T07:34:31.153765518Z

This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).

---

## 1. Network Contagion: 3-Degree Separation

**Objective:** Find all patients who share a doctor with a patient (PT-10001) who shares a doctor with an RA patient (requires: PT-10001→Doc1→Patient2→Doc2→RAPatient).

**Query:** "Find patients within 3 degrees of separation from PT-10001 via shared doctors"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 45ms | 74ms | ↑64.4% |
| **Chunks Retrieved** | 5 | 11 | +6 |

### Raw Results

**Vector-Only (Naive RAG):**
```
b1c658df-d1dd-427e-a4f3-67170a4ba0fd | node=a4942d1a-f7cd-420a-bc73-c97d06b547c9 | **PROVIDER PROFILE** Provider ID: DOC-110 Specialty: Endocrinology Years of Experience: 17 Board Certified: Yes
1db9e704-d116-44e3-ae42-ef510561d9fc | node=f5908e50-2985-48d6-83d4-37937fc9b30c | **PROVIDER PROFILE** Provider ID: DOC-105 Specialty: Endocrinology Years of Experience: 7 Board Certified: Yes
ceb8ac45-85a0-4915-86e6-d222759af3b1 | node=045ced6e-f091-4e67-9b38-331faa741735 | **PROVIDER PROFILE** Provider ID: DOC-103 Specialty: Internal Medicine Years of Experience: 5 Board Certified: Yes
af0418c9-b64b-4a37-90e8-a98babf1b398 | node=b738ee49-1893-43ec-8e0f-95e2630c3ce8 | **PROVIDER PROFILE** Provider ID: DOC-107 Specialty: Internal Medicine Years of Experience: 20 Board Certified: Yes
b9df15c1-b3c8-47f2-b8e8-6f139590e256 | node=9537407f-7d1c-493f-837d-05f62d882fdf | **PROVIDER PROFILE** Provider ID: DOC-101 Specialty: Endocrinology Years of Experience: 12 Board Certified: Yes
```

**Knowledge Model (Graph RAG):**
```
59c17749-bf15-408c-9883-18fdd8d439e8 | node=c73562dd-2fbd-4079-9c45-fcb601b97b90 | **CONDITION PROFILE** Name: Bronchial Asthma ICD-10 Code: J45.909 Common Symptoms: wheezing, coughing, chest tightness Risk Factors: Smoking, Allergens Contrain...
52a2a65d-ac3e-4032-b608-ef89e919f184 | node=67303fb7-8add-495c-b832-883141d76d16 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10069 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
af0418c9-b64b-4a37-90e8-a98babf1b398 | node=b738ee49-1893-43ec-8e0f-95e2630c3ce8 | **PROVIDER PROFILE** Provider ID: DOC-107 Specialty: Internal Medicine Years of Experience: 20 Board Certified: Yes
b9df15c1-b3c8-47f2-b8e8-6f139590e256 | node=9537407f-7d1c-493f-837d-05f62d882fdf | **PROVIDER PROFILE** Provider ID: DOC-101 Specialty: Endocrinology Years of Experience: 12 Board Certified: Yes
7e36a0c4-6e1e-4b99-ac7f-ec1ad67c7147 | node=21283144-fc05-4699-a721-bf9e7496b65e | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10018 **Provider ID:** DOC-103 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Congestive...
13b4ca44-5e7d-4287-a572-9ffed7feeadb | node=91341a58-a216-4faf-8a19-739fdca55bf4 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10023 **Provider ID:** DOC-103 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
83ce7b72-8385-4d09-9c59-7979563fc5ec | node=9cde77f7-b83a-490a-8a19-8f828863a961 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10105 **Provider ID:** DOC-107 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Bronchial ...
964d66f6-8174-4057-bc06-2a97d6b1aea7 | node=59252bed-0fa0-43b4-823c-50d5f0f88c49 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10099 **Provider ID:** DOC-110 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Congestive...
ceb8ac45-85a0-4915-86e6-d222759af3b1 | node=045ced6e-f091-4e67-9b38-331faa741735 | **PROVIDER PROFILE** Provider ID: DOC-103 Specialty: Internal Medicine Years of Experience: 5 Board Certified: Yes
0c2b44bc-6ed0-459e-9ebb-e08ed30652a7 | node=d5db5320-179c-432e-bcd5-5c0e7e936308 | **CONDITION PROFILE** Name: Congestive Heart Failure ICD-10 Code: I50.9 Common Symptoms: edema, fatigue, dyspnea Risk Factors: Hypertension, CAD Contraindicated...
b1c658df-d1dd-427e-a4f3-67170a4ba0fd | node=a4942d1a-f7cd-420a-bc73-c97d06b547c9 | **PROVIDER PROFILE** Provider ID: DOC-110 Specialty: Endocrinology Years of Experience: 17 Board Certified: Yes
```

---

## 2. Polypharmacy Risk Pattern

**Objective:** Find patients with RA taking Albuterol who ALSO have Diabetes, are treated by the same doctor as a CHF patient, and have elevated HbA1c (requires intersection of 5 independent graph paths).

**Query:** "Find complex polypharmacy risk patients with RA taking Albuterol plus comorbidities"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 21ms | 37ms | ↑76.2% |
| **Chunks Retrieved** | 5 | 14 | +9 |

### Raw Results

**Vector-Only (Naive RAG):**
```
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
4ac62a4c-316c-4421-a8ba-c58cf0fd30f4 | node=a91b8657-b8dd-40fc-ba1a-f8e8ddab0ccb | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10026 **Provider ID:** DOC-105 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b195ec8f-48db-4922-bca2-070df7a3717a | node=fe535d04-3584-48cf-bc54-b72caf8483b6 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-107 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
1aa8346c-ef36-450d-bd32-69cbd777b2bb | node=13cb51d0-ba1d-4a8f-bd2e-34ba2eb7de85 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10090 **Provider ID:** DOC-112 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

**Knowledge Model (Graph RAG):**
```
63a247e8-0015-4236-95db-da62d3bb682a | node=715c3ef8-0bdf-4284-b623-bc5af480f2ed | **MEDICATION PROFILE** Name: Propranolol Drug Class: Beta Blocker Indication: Hypertension Available Dosages: 40mg, 80mg Common Side Effects: fatigue, bradycard...
1aa8346c-ef36-450d-bd32-69cbd777b2bb | node=13cb51d0-ba1d-4a8f-bd2e-34ba2eb7de85 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10090 **Provider ID:** DOC-112 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
1d6887af-0fe5-460d-83f4-692ea5c3f160 | node=4d018ff7-3db9-4016-82f8-9fe5019c2ed7 | **MEDICATION PROFILE** Name: Albuterol Drug Class: Bronchodilator Indication: Bronchial Asthma Available Dosages: 90mcg inhaler Common Side Effects: tremors, pa...
af0418c9-b64b-4a37-90e8-a98babf1b398 | node=b738ee49-1893-43ec-8e0f-95e2630c3ce8 | **PROVIDER PROFILE** Provider ID: DOC-107 Specialty: Internal Medicine Years of Experience: 20 Board Certified: Yes
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
7a1af4c6-3b37-4003-a44b-267abcfd43e6 | node=f61a7b22-b075-4412-9ce6-8ce3ae9436c1 | **CONDITION PROFILE** Name: Rheumatoid Arthritis ICD-10 Code: M06.9 Common Symptoms: joint pain, stiffness, swelling Risk Factors: Smoking, Family History Contr...
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
1db9e704-d116-44e3-ae42-ef510561d9fc | node=f5908e50-2985-48d6-83d4-37937fc9b30c | **PROVIDER PROFILE** Provider ID: DOC-105 Specialty: Endocrinology Years of Experience: 7 Board Certified: Yes
a2249ef3-a64f-4eef-96ec-77e2ec9aaf6e | node=9092f452-63ad-424c-ad0f-b3ea42ce18a9 | **MEDICATION PROFILE** Name: Atorvastatin Drug Class: Statin Indication: Hyperlipidemia Available Dosages: 10mg, 20mg, 40mg Common Side Effects: muscle pain, li...
e03016ae-33b2-4d98-86f3-14af17e0c32b | node=69bd22d1-143a-4421-be6f-81b9b453c360 | **PROVIDER PROFILE** Provider ID: DOC-113 Specialty: Cardiology Years of Experience: 32 Board Certified: Yes
e7bfa4e5-d40b-402d-89d0-e4b80c20bba8 | node=cb109453-cb76-4ae1-9d0d-d85ce3ec69b5 | **PROVIDER PROFILE** Provider ID: DOC-112 Specialty: Internal Medicine Years of Experience: 30 Board Certified: Yes
4ac62a4c-316c-4421-a8ba-c58cf0fd30f4 | node=a91b8657-b8dd-40fc-ba1a-f8e8ddab0ccb | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10026 **Provider ID:** DOC-105 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b9df15c1-b3c8-47f2-b8e8-6f139590e256 | node=9537407f-7d1c-493f-837d-05f62d882fdf | **PROVIDER PROFILE** Provider ID: DOC-101 Specialty: Endocrinology Years of Experience: 12 Board Certified: Yes
b195ec8f-48db-4922-bca2-070df7a3717a | node=fe535d04-3584-48cf-bc54-b72caf8483b6 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-107 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

---

## 3. Provider Network Cascade

**Objective:** Find all patients treated by doctors who work in the same clinic as doctors treating RA patients with complications (requires: RAPatient→Doctor→Clinic→OtherDoctors→OtherPatients).

**Query:** "Find patients in provider networks connected to complex RA cases"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 23ms | 34ms | ↑47.8% |
| **Chunks Retrieved** | 5 | 11 | +6 |

### Raw Results

**Vector-Only (Naive RAG):**
```
7a1af4c6-3b37-4003-a44b-267abcfd43e6 | node=f61a7b22-b075-4412-9ce6-8ce3ae9436c1 | **CONDITION PROFILE** Name: Rheumatoid Arthritis ICD-10 Code: M06.9 Common Symptoms: joint pain, stiffness, swelling Risk Factors: Smoking, Family History Contr...
4ac62a4c-316c-4421-a8ba-c58cf0fd30f4 | node=a91b8657-b8dd-40fc-ba1a-f8e8ddab0ccb | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10026 **Provider ID:** DOC-105 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
385d4e97-c87b-4ace-adf4-44d93dc6099b | node=41191b97-ce3e-41c8-8622-c1496a8358db | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10046 **Provider ID:** DOC-104 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

**Knowledge Model (Graph RAG):**
```
6797fc0f-34dc-4328-bcbd-7338d4183eb7 | node=6f9f0ba2-c49b-4e6e-ac30-8d2defb06c57 | **PROVIDER PROFILE** Provider ID: DOC-106 Specialty: Family Practice Years of Experience: 28 Board Certified: Yes
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
e03016ae-33b2-4d98-86f3-14af17e0c32b | node=69bd22d1-143a-4421-be6f-81b9b453c360 | **PROVIDER PROFILE** Provider ID: DOC-113 Specialty: Cardiology Years of Experience: 32 Board Certified: Yes
d80feaf6-14ec-4ac2-a935-8d036e20432b | node=98ddb29a-dc5f-4dcf-985b-f1af104e60a8 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10054 **Provider ID:** DOC-104 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
7a1af4c6-3b37-4003-a44b-267abcfd43e6 | node=f61a7b22-b075-4412-9ce6-8ce3ae9436c1 | **CONDITION PROFILE** Name: Rheumatoid Arthritis ICD-10 Code: M06.9 Common Symptoms: joint pain, stiffness, swelling Risk Factors: Smoking, Family History Contr...
b0cfc4d8-f729-4042-8e46-d0fbcca21098 | node=377881dd-fe4f-457a-b743-b10dd8d41af9 | **PROVIDER PROFILE** Provider ID: DOC-104 Specialty: Internal Medicine Years of Experience: 19 Board Certified: Yes
e7ad39b4-6185-4ee5-9a04-bcc5c20ee4f1 | node=82a7b5ea-44bd-4f76-b9b3-6260f128a47d | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10118 **Provider ID:** DOC-106 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b9df15c1-b3c8-47f2-b8e8-6f139590e256 | node=9537407f-7d1c-493f-837d-05f62d882fdf | **PROVIDER PROFILE** Provider ID: DOC-101 Specialty: Endocrinology Years of Experience: 12 Board Certified: Yes
a3f12a68-e01f-4940-93e5-d34d5160f3e8 | node=e2e8e06e-8b39-41f6-ae74-aa7a94c630f9 | **PROVIDER PROFILE** Provider ID: DOC-102 Specialty: Family Practice Years of Experience: 16 Board Certified: Yes
5daa4654-32b0-4d85-be52-190e3d78a1e1 | node=f52d0327-6680-43c1-90ca-49328fbbbfcd | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10047 **Provider ID:** DOC-102 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

---

## 4. Bidirectional Risk Network

**Objective:** Find patients who (a) share a doctor with RA patients AND (b) take medications that interact with RA treatments (requires: Patient→Doctor←RAPatient→Medication→InteractsWith←Patient→Medication).

**Query:** "Find patients with bidirectional risk exposure to RA treatment networks"

### Results Comparison

| Metric | Vector-Only (Naive RAG) | Knowledge Model (Graph RAG) | Improvement |
|--------|------------------------|----------------------------|-------------|
| **Latency** | 26ms | 36ms | ↑38.5% |
| **Chunks Retrieved** | 5 | 15 | +10 |

### Raw Results

**Vector-Only (Naive RAG):**
```
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
66d74161-268a-4bdf-8850-8f2d48ef68fc | node=467ad959-5d17-4526-b3a0-92f10f9442ee | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10119 **Provider ID:** DOC-103 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
b195ec8f-48db-4922-bca2-070df7a3717a | node=fe535d04-3584-48cf-bc54-b72caf8483b6 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-107 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
1aa8346c-ef36-450d-bd32-69cbd777b2bb | node=13cb51d0-ba1d-4a8f-bd2e-34ba2eb7de85 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10090 **Provider ID:** DOC-112 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
```

**Knowledge Model (Graph RAG):**
```
a2249ef3-a64f-4eef-96ec-77e2ec9aaf6e | node=9092f452-63ad-424c-ad0f-b3ea42ce18a9 | **MEDICATION PROFILE** Name: Atorvastatin Drug Class: Statin Indication: Hyperlipidemia Available Dosages: 10mg, 20mg, 40mg Common Side Effects: muscle pain, li...
66d74161-268a-4bdf-8850-8f2d48ef68fc | node=467ad959-5d17-4526-b3a0-92f10f9442ee | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10119 **Provider ID:** DOC-103 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
3347007f-599a-45e3-adb4-2e64ad009a6d | node=b4bbb595-d121-4337-bd02-df916dcf65c3 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10029 **Provider ID:** DOC-101 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
af0418c9-b64b-4a37-90e8-a98babf1b398 | node=b738ee49-1893-43ec-8e0f-95e2630c3ce8 | **PROVIDER PROFILE** Provider ID: DOC-107 Specialty: Internal Medicine Years of Experience: 20 Board Certified: Yes
1d6887af-0fe5-460d-83f4-692ea5c3f160 | node=4d018ff7-3db9-4016-82f8-9fe5019c2ed7 | **MEDICATION PROFILE** Name: Albuterol Drug Class: Bronchodilator Indication: Bronchial Asthma Available Dosages: 90mcg inhaler Common Side Effects: tremors, pa...
ceb8ac45-85a0-4915-86e6-d222759af3b1 | node=045ced6e-f091-4e67-9b38-331faa741735 | **PROVIDER PROFILE** Provider ID: DOC-103 Specialty: Internal Medicine Years of Experience: 5 Board Certified: Yes
1aa8346c-ef36-450d-bd32-69cbd777b2bb | node=13cb51d0-ba1d-4a8f-bd2e-34ba2eb7de85 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10090 **Provider ID:** DOC-112 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
5147cb6f-3700-457e-9c76-7c19f128bc54 | node=e233725f-056e-4096-9b34-8b331dfccb4b | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10109 **Provider ID:** DOC-113 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
63a247e8-0015-4236-95db-da62d3bb682a | node=715c3ef8-0bdf-4284-b623-bc5af480f2ed | **MEDICATION PROFILE** Name: Propranolol Drug Class: Beta Blocker Indication: Hypertension Available Dosages: 40mg, 80mg Common Side Effects: fatigue, bradycard...
b195ec8f-48db-4922-bca2-070df7a3717a | node=fe535d04-3584-48cf-bc54-b72caf8483b6 | **CLINICAL ENCOUNTER NOTE** **Patient ID:** PT-10094 **Provider ID:** DOC-107 **Date:** 2025-11-27  **SUBJECTIVE:** Patient presents for follow-up of Rheumatoid...
7a1af4c6-3b37-4003-a44b-267abcfd43e6 | node=f61a7b22-b075-4412-9ce6-8ce3ae9436c1 | **CONDITION PROFILE** Name: Rheumatoid Arthritis ICD-10 Code: M06.9 Common Symptoms: joint pain, stiffness, swelling Risk Factors: Smoking, Family History Contr...
cf2376c0-e2cc-4624-959d-aefe7da45142 | node=e8d1c4ef-a0a9-4607-bc5a-a3a257a3fdcc | **MEDICATION PROFILE** Name: Ibuprofen Drug Class: NSAID Indication: Pain Available Dosages: 200mg, 400mg, 600mg Common Side Effects: stomach pain, heartburn
b9df15c1-b3c8-47f2-b8e8-6f139590e256 | node=9537407f-7d1c-493f-837d-05f62d882fdf | **PROVIDER PROFILE** Provider ID: DOC-101 Specialty: Endocrinology Years of Experience: 12 Board Certified: Yes
e03016ae-33b2-4d98-86f3-14af17e0c32b | node=69bd22d1-143a-4421-be6f-81b9b453c360 | **PROVIDER PROFILE** Provider ID: DOC-113 Specialty: Cardiology Years of Experience: 32 Board Certified: Yes
e7bfa4e5-d40b-402d-89d0-e4b80c20bba8 | node=cb109453-cb76-4ae1-9d0d-d85ce3ec69b5 | **PROVIDER PROFILE** Provider ID: DOC-112 Specialty: Internal Medicine Years of Experience: 30 Board Certified: Yes
```

---

