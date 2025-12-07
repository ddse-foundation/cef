# CEF Benchmark Report: Medical Dataset

**Backend:** neo4j
**Generated:** 2025-12-07T15:23:00.861973482Z

---

## Dataset Statistics

- **Nodes:** 177
- **Edges:** 455
- **Labels:** Condition, Medication, Patient, Doctor

## Scenario Results

### Hidden Drug Interaction Chain

**Objective:** Find patients at risk for heart surgery complications due to undiscovered medication interactions. Requires: Surgery→PreOp_Medication→INTERACTS_WITH→Patient_Medication→Patient path

**Query:** "Which patients scheduled for cardiac procedures have medications that interact with standard pre-op drugs"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 9 | +4 (80%) |
| Latency (p50) | 18 ms | 37 ms | +19 ms |
| Latency (p95) | 19 ms | 43 ms | +24 ms |

**Patterns Executed:**
- `Patient→SCHEDULED_FOR→*`
- `Patient→HAS_CONDITION→*`
- `Patient→PRESCRIBED_MEDICATION→*`
- `Patient→INTERACTS_WITH→*`

---

### Transitive Infection Exposure Risk

**Objective:** Find patients potentially exposed to infectious diseases through shared healthcare providers. Requires traversing: Patient→TREATED_BY→Doctor→TREATED_BY→InfectedPatient→HAS_CONDITION→InfectiousDisease

**Query:** "Identify patients who share doctors with patients diagnosed with communicable diseases"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 15 | +10 (200%) |
| Latency (p50) | 19 ms | 78 ms | +59 ms |
| Latency (p95) | 25 ms | 81 ms | +56 ms |

**Patterns Executed:**
- `Patient→TREATED_BY→*`
- `Patient→HAS_CONDITION→*`

---

### Polypharmacy Cascade Risk

**Objective:** Find elderly patients at high fall risk due to multiple medication side effects combined with bone conditions. Requires aggregating: Patient→PRESCRIBED→Meds→CAUSES_SIDE_EFFECT→[Dizziness,Drowsiness] AND Patient→HAS_CONDITION→Osteoporosis

**Query:** "Which senior patients on multiple medications are at elevated fall risk due to combined drug side effects"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 17 | +12 (240%) |
| Latency (p50) | 21 ms | 64 ms | +43 ms |
| Latency (p95) | 22 ms | 71 ms | +49 ms |

**Patterns Executed:**
- `Patient→PRESCRIBED_MEDICATION→*`
- `Patient→CAUSES_SIDE_EFFECT→*`
- `Patient→HAS_CONDITION→*`

---

### Treatment Protocol Gap Detection

**Objective:** Find patients with conditions who are NOT receiving standard-of-care medications. Requires: Condition→STANDARD_TREATMENT→Medication comparison with Patient→PRESCRIBED→Medications

**Query:** "Identify patients with diabetes who are missing recommended first-line treatment medications"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 14 | +9 (180%) |
| Latency (p50) | 17 ms | 51 ms | +34 ms |
| Latency (p95) | 24 ms | 54 ms | +30 ms |

**Patterns Executed:**
- `Patient→HAS_CONDITION→*`
- `Patient→PRESCRIBED_MEDICATION→*`
- `Patient→STANDARD_TREATMENT→*`

---

### Cross-Specialty Contraindication

**Objective:** Find patients whose medications from different specialists are contraindicated with each other. Requires: Patient→TREATED_BY→Doctor[specialty=A]→PRESCRIBED→Med1 CROSS Patient→TREATED_BY→Doctor[specialty=B]→PRESCRIBED→Med2 WHERE Med1→CONTRAINDICATED→Med2

**Query:** "Which patients have potentially dangerous drug combinations from different specialist prescriptions"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 18 | +13 (260%) |
| Latency (p50) | 18 ms | 65 ms | +47 ms |
| Latency (p95) | 20 ms | 112 ms | +92 ms |

**Patterns Executed:**
- `Patient→TREATED_BY→*`
- `Patient→PRESCRIBED_MEDICATION→*`
- `Patient→CONTRAINDICATED_FOR→*`

---

## Summary

| Metric | Value |
|--------|-------|
| Avg Chunk Improvement | 192.0% |
| Avg Latency Overhead | 216.4% |
| Total Benchmark Time | 3,101 ms |

