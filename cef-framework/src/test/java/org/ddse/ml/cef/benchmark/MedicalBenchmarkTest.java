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

            // Scenario 1: Multi-Hop Contraindication Chain (3-hop)
            runScenario(writer,
                    "1. Multi-Hop Contraindication Discovery",
                    "Find patients with conditions whose medications are contraindicated for OTHER conditions they have (requires: Patient→Condition1→Patient→Medication→ContraindicatedFor→Condition2).",
                    "Find patients whose medications contradict their other conditions",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "CONTRAINDICATED_FOR");

            // Scenario 2: Behavioral + Clinical Intersection (4-hop)
            runScenario(writer,
                    "2. High-Risk Behavioral Pattern",
                    "Find patients who smoke AND have asthma AND are prescribed bronchodilators that worsen with smoking (requires: Patient→SmokingStatus + Patient→Condition + Patient→Medication→InteractsWith→Smoking).",
                    "Find smoking asthma patients on medications that interact with smoking",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "HAS_INTERACTION");

            // Scenario 3: Medication Side Effect Cascade (5-hop)
            runScenario(writer,
                    "3. Cascading Side Effect Risk",
                    "Find patients taking Prednisone for Condition A, where Prednisone causes Side Effect B, which requires Treatment C, but Treatment C is contraindicated for their Condition D (requires: Patient→Med→CausesSideEffect→Condition→RequiresMed→ContraindicatedFor→Patient→HasCondition).",
                    "Find patients with cascading medication side effect risks from Prednisone",
                    "Medication", "PRESCRIBED_MEDICATION", "CAUSES_SIDE_EFFECT", "HAS_CONDITION",
                    "CONTRAINDICATED_FOR");

            // Scenario 4: Cross-Patient Contagion Risk (4-hop transitive)
            runScenario(writer,
                    "4. Transitive Exposure Risk",
                    "Find patients treated by doctors who also treat high-risk infectious patients (CHF patients on immunosuppressants), requiring: Patient→Doctor→InfectiousPatient→Medication→ImmunosuppressantClass.",
                    "Find patients sharing doctors with immunocompromised CHF patients",
                    "Patient", "TREATED_BY", "HAS_CONDITION", "PRESCRIBED_MEDICATION");

            logger.info("Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
