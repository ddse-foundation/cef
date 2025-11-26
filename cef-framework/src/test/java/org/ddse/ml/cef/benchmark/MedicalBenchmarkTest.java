package org.ddse.ml.cef.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Benchmark test comparing Vector-Only (Naive RAG) vs Knowledge Model (Graph
 * RAG)
 * on medical clinical decision support scenarios.
 * 
 * Data is loaded once per class via MedicalDataTestBase inheritance.
 */
@DisplayName("Benchmark: Medical Knowledge Model")
class MedicalBenchmarkTest extends BenchmarkBase {

    private static final String REPORT_FILE = "BENCHMARK_REPORT.md";

    @Test
    @DisplayName("Generate Medical Benchmark Report")
    void generateBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "Knowledge Model vs. Vector Retrieval Benchmark", "Medical (Clinical Decision Support)",
                    "This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning.");

            // Scenario 1: Safety Check (Contraindications)
            runScenario(writer,
                    "1. Safety Check: Contraindicated Medications",
                    "Identify patients taking medications that are contraindicated for their specific conditions.",
                    "Find patients with conditions taking contraindicated medications",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "CONTRAINDICATED_FOR");

            // Scenario 2: Behavioral Risk (Smokers with Asthma)
            runScenario(writer,
                    "2. Behavioral Risk: Smokers with Asthma",
                    "Find patients with 'Bronchial Asthma' who are current smokers.",
                    "Find patients with Bronchial Asthma who smoke",
                    "Patient", "HAS_CONDITION");

            // Scenario 3: Root Cause (Side Effects)
            runScenario(writer,
                    "3. Root Cause Analysis",
                    "List side effects reported by patients taking 'Prednisone' who also have 'Type 2 Diabetes'.",
                    "Side effects of Prednisone for Diabetes patients",
                    "Medication", "PRESCRIBED_MEDICATION", "HAS_CONDITION");

            logger.info("Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
