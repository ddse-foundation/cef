# Benchmark Evaluation Summary

Generated: 2025-11-26T19:36:46.834600Z
Reports evaluated: 3
Total scenarios: 8

## Knowledge Model vs. Vector Retrieval Benchmark

- Path: `/mnt/backup1/home/manna/workspace/ml/jugbd/ced/cef-framework/BENCHMARK_REPORT.md`
- Domain: Medical (Clinical Decision Support)
- Generated at: 2025-11-26T19:07:17.080972498Z
- Average latency (vector -> knowledge): 36.7ms -> 73.3ms
- Average improvement (reported -> computed): +112.3% -> +112.3%
- Chunk parity: 100.0% | Knowledge faster: 0.0%

### Scenarios

| # | Scenario | Query | Latency (ms) | Delta (ms) | Reported | Computed | Gap (pp) | Chunks (V/KM) | Raw Rows (V/KM) | Parity |
|---|---------|-------|--------------|-----------|----------|----------|----------|----------------|------------------|--------|
| 1 | Safety Check: Contraindicated Medications | Find patients with conditions taking contraindicated medications | 55.0 -> 89.0 | +34.0 | +61.8% | +61.8% | +0.0 | 5/5 | 5/5 | yes |
| 2 | Behavioral Risk: Smokers with Asthma | Find patients with Bronchial Asthma who smoke | 26.0 -> 59.0 | +33.0 | +126.9% | +126.9% | +0.0 | 5/5 | 5/5 | yes |
| 3 | Root Cause Analysis | Side effects of Prednisone for Diabetes patients | 29.0 -> 72.0 | +43.0 | +148.3% | +148.3% | -0.0 | 5/5 | 5/5 | yes |

## Knowledge Model vs. Vector Retrieval Benchmark (Advanced)

- Path: `/mnt/backup1/home/manna/workspace/ml/jugbd/ced/cef-framework/BENCHMARK_REPORT_2.md`
- Domain: Medical (Clinical Decision Support)
- Generated at: 2025-11-26T19:09:03.824369741Z
- Average latency (vector -> knowledge): 39.0ms -> 70.3ms
- Average improvement (reported -> computed): +106.6% -> +106.6%
- Chunk parity: 100.0% | Knowledge faster: 0.0%

### Scenarios

| # | Scenario | Query | Latency (ms) | Delta (ms) | Reported | Computed | Gap (pp) | Chunks (V/KM) | Raw Rows (V/KM) | Parity |
|---|---------|-------|--------------|-----------|----------|----------|----------|----------------|------------------|--------|
| 1 | Network Hop: Patient Zero Link | Find all patients treated by the same doctor as PT-10001 | 68.0 -> 93.0 | +25.0 | +36.8% | +36.8% | -0.0 | 5/5 | 5/5 | yes |
| 2 | Intersection: Condition + Medication | Find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol | 25.0 -> 52.0 | +27.0 | +108.0% | +108.0% | +0.0 | 5/5 | 5/5 | yes |
| 3 | Aggregation: Provider Pattern | List doctors who are treating more than one patient with Rheumatoid Arthritis | 24.0 -> 66.0 | +42.0 | +175.0% | +175.0% | +0.0 | 5/5 | 5/5 | yes |

## Knowledge Model vs. Vector Retrieval Benchmark (SAP)

- Path: `/mnt/backup1/home/manna/workspace/ml/jugbd/ced/cef-framework/SAP_BENCHMARK_REPORT.md`
- Domain: Enterprise ERP (Financial & Supply Chain)
- Generated at: 2025-11-26T19:22:41.935573032Z
- Average latency (vector -> knowledge): 38.5ms -> 75.5ms
- Average improvement (reported -> computed): +99.0% -> +98.9%
- Chunk parity: 100.0% | Knowledge faster: 0.0%

### Scenarios

| # | Scenario | Query | Latency (ms) | Delta (ms) | Reported | Computed | Gap (pp) | Chunks (V/KM) | Raw Rows (V/KM) | Parity |
|---|---------|-------|--------------|-----------|----------|----------|----------|----------------|------------------|--------|
| 1 | Shadow IT Detection | Analyze spend trend for Engineering department and flag suspicious vendors | 51.0 -> 97.0 | +46.0 | +90.2% | +90.2% | -0.0 | 5/5 | 5/5 | yes |
| 2 | Typhoon Impact: Holiday Laptop Delivery | Impact of Typhoon in Taiwan on Holiday Laptop delivery | 26.0 -> 54.0 | +28.0 | +107.7% | +107.7% | -0.0 | 5/5 | 5/5 | yes |

## Overall Summary

- Scenarios evaluated: 8
- Latency (vector -> knowledge): 38.0ms -> 72.8ms
- Improvement (reported -> computed): +106.8% -> +106.8%
- Chunk parity: 100.0% | Knowledge faster: 0.0%
