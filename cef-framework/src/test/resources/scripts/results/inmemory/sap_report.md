# CEF Benchmark Report: Sap Dataset

**Backend:** inmemory
**Generated:** 2025-12-07T14:50:18.429334153Z

---

## Dataset Statistics

- **Nodes:** 60
- **Edges:** 121
- **Labels:** Project, Department, Invoice, CostCenter, CustomerOrder, Product, Event, Material, Vendor, FinancialStatus, Location

## Scenario Results

### Supply Chain Disruption Impact

**Objective:** Find customer orders at risk due to natural disaster affecting component suppliers. Requires: Event→AFFECTS_LOCATION→Location→LOCATED_IN→Vendor→SUPPLIES→Material→COMPOSED_OF→Product→ORDERS

**Query:** "Which customer orders will be delayed due to the Taiwan typhoon affecting our component suppliers"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 1 | -4 (-80%) |
| Latency (p50) | 12 ms | 15 ms | +3 ms |
| Latency (p95) | 13 ms | 15 ms | +2 ms |
| Graph Nodes Traversed | - | 2 | - |

**Patterns Executed:**
- `Event→AFFECTS_LOCATION→*`
- `Event→LOCATED_IN→*`
- `Event→SUPPLIES→*`
- `Event→COMPOSED_OF→*`
- `Event→ORDERS→*`

---

### Budget Contagion via Shared Vendors

**Objective:** Find departments at financial risk due to shared vendor dependencies with overrun departments. Requires: Dept[overrun]→HAS_COST_CENTER→CC→PAYS→Vendor→SUPPLIES→Material→USED_IN→Project→FUNDED_BY→Dept2

**Query:** "Which departments share vendors with the Engineering department that has budget overruns"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 8 | +3 (60%) |
| Latency (p50) | 13 ms | 15 ms | +2 ms |
| Latency (p95) | 14 ms | 17 ms | +3 ms |
| Graph Nodes Traversed | - | 9 | - |

**Patterns Executed:**
- `Department→HAS_COST_CENTER→*`
- `Department→PAYS→*`
- `Department→SUPPLIES→*`
- `Department→USED_IN→*`
- `Department→FUNDED_BY→*`
- `Department→HAS_OVERRUN→*`

---

### Critical Component Single Sourcing Risk

**Objective:** Find products with components that have only one supplier (single point of failure). Requires: Product→COMPOSED_OF→Material→SUPPLIED_BY→Vendor with cardinality check

**Query:** "Which products have critical components sourced from only one vendor creating supply chain vulnerability"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 5 | +0 (0%) |
| Latency (p50) | 14 ms | 29 ms | +15 ms |
| Latency (p95) | 15 ms | 30 ms | +15 ms |

**Patterns Executed:**
- `Product→COMPOSED_OF→*`
- `Product→SUPPLIED_BY→*`

---

### Project Overrun Root Cause Analysis

**Objective:** Find external factors (events, vendor issues) contributing to project budget overruns. Requires: Project[OVERRUN]←USED_IN←Material→SUPPLIED_BY→Vendor→LOCATED_IN→Location←AFFECTS←Event

**Query:** "What external events or vendor issues are causing the Holiday Laptop project to run over budget"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 5 | +0 (0%) |
| Latency (p50) | 13 ms | 26 ms | +13 ms |
| Latency (p95) | 14 ms | 28 ms | +14 ms |

**Patterns Executed:**
- `Project→USED_IN→*`
- `Project→SUPPLIED_BY→*`
- `Project→LOCATED_IN→*`
- `Project→AFFECTS_LOCATION→*`

---

### Cross-Department Invoice Anomaly Detection

**Objective:** Find invoices paid to vendors not in the department's approved vendor list. Requires: Invoice→PAID_TO→Vendor comparison with Department→HAS_BUDGET→Budget[approved_vendors]

**Query:** "Which invoices were paid to vendors outside the department's pre-approved supplier list"

| Metric | Vector-Only | Knowledge Model | Improvement |
|--------|-------------|-----------------|-------------|
| Chunks Retrieved | 5 | 9 | +4 (80%) |
| Latency (p50) | 14 ms | 15 ms | +1 ms |
| Latency (p95) | 15 ms | 23 ms | +8 ms |
| Graph Nodes Traversed | - | 9 | - |

**Patterns Executed:**
- `Invoice→PAID_TO→*`
- `Invoice→INCURRED_BY→*`
- `Invoice→BELONGS_TO→*`
- `Invoice→HAS_BUDGET→*`

---

## Summary

| Metric | Value |
|--------|-------|
| Avg Chunk Improvement | 12.0% |
| Avg Latency Overhead | 50.9% |
| Total Benchmark Time | 1,192 ms |

