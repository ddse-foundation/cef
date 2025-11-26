package org.ddse.ml.cef.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Advanced benchmark scenarios testing multi-hop, intersection, and aggregation
 * patterns.
 * 
 * Data is loaded once per class via MedicalDataTestBase inheritance.
 */
@DisplayName("Benchmark: Medical Knowledge Model (Advanced Scenarios)")
class MedicalBenchmarkTest2 extends BenchmarkBase {

    private static final String REPORT_FILE = "BENCHMARK_REPORT_2.md";

    @Test
    @DisplayName("Generate Medical Benchmark Report 2")
    void generateBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "Knowledge Model vs. Vector Retrieval Benchmark (Advanced)",
                    "Medical (Clinical Decision Support)",
                    "This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).");

            // Scenario 1: "Patient Zero" Link (Network Hop)
            runScenario(writer,
                    "1. Network Hop: Patient Zero Link",
                    "Find all patients treated by the same doctor as 'PT-10001'.",
                    "Find all patients treated by the same doctor as PT-10001",
                    "Patient", "TREATED_BY");

            // Scenario 2: "Hidden Comorbidity" (Intersection)
            runScenario(writer,
                    "2. Intersection: Condition + Medication",
                    "Find patients diagnosed with 'Rheumatoid Arthritis' who are also prescribed 'Albuterol'.",
                    "Find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION");

            // Scenario 3: "Provider Pattern" (Aggregation)
            runScenario(writer,
                    "3. Aggregation: Provider Pattern",
                    "List doctors who are treating more than one patient with 'Rheumatoid Arthritis'.",
                    "List doctors who are treating more than one patient with Rheumatoid Arthritis",
                    "Patient", "TREATED_BY", "HAS_CONDITION");

            logger.info("Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
