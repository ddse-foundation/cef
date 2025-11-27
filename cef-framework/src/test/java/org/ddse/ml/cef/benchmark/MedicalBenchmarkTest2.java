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

                        // Scenario 1: Triple-Hop Network Contagion
                        runScenario(writer,
                                        "1. Network Contagion: 3-Degree Separation",
                                        "Find all patients who share a doctor with a patient (PT-10001) who shares a doctor with an RA patient (requires: PT-10001→Doc1→Patient2→Doc2→RAPatient).",
                                        "Find patients within 3 degrees of separation from PT-10001 via shared doctors",
                                        "Patient", "TREATED_BY", "HAS_CONDITION");

                        // Scenario 2: Complex Polypharmacy Intersection (5-constraint)
                        runScenario(writer,
                                        "2. Polypharmacy Risk Pattern",
                                        "Find patients with RA taking Albuterol who ALSO have Diabetes, are treated by the same doctor as a CHF patient, and have elevated HbA1c (requires intersection of 5 independent graph paths).",
                                        "Find complex polypharmacy risk patients with RA taking Albuterol plus comorbidities",
                                        "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "TREATED_BY");

                        // Scenario 3: Hierarchical Provider Network Analysis
                        runScenario(writer,
                                        "3. Provider Network Cascade",
                                        "Find all patients treated by doctors who work in the same clinic as doctors treating RA patients with complications (requires: RAPatient→Doctor→Clinic→OtherDoctors→OtherPatients).",
                                        "Find patients in provider networks connected to complex RA cases",
                                        "Patient", "TREATED_BY", "WORKS_AT", "HAS_CONDITION");

                        // Scenario 4: Bidirectional Risk Propagation
                        runScenario(writer,
                                        "4. Bidirectional Risk Network",
                                        "Find patients who (a) share a doctor with RA patients AND (b) take medications that interact with RA treatments (requires: Patient→Doctor←RAPatient→Medication→InteractsWith←Patient→Medication).",
                                        "Find patients with bidirectional risk exposure to RA treatment networks",
                                        "Patient", "TREATED_BY", "HAS_CONDITION", "PRESCRIBED_MEDICATION",
                                        "INTERACTS_WITH");

                        logger.info("Benchmark report generated at: " + REPORT_FILE);
                }
        }
}
