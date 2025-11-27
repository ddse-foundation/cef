package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.parser.impl.CsvParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@DisplayName("Benchmark: SAP Knowledge Model")
class SapBenchmarkTest extends BenchmarkBase {

    private static final String REPORT_FILE = "SAP_BENCHMARK_REPORT.md";

    @Autowired
    private CsvParser csvParser;

    @BeforeEach
    void setup() throws IOException {
        // Reset shared stores loaded by MedicalDataTestBase so the SAP dataset stands
        // alone
        graphStore.clear().block();
        chunkStore.deleteAll().block();

        SapDataParser parser = new SapDataParser(graphStore, csvParser, indexer);
        parser.parseAndLoad();
    }

    @Test
    @DisplayName("Generate SAP Benchmark Report")
    void generateSapBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "Knowledge Model vs. Vector Retrieval Benchmark (SAP)",
                    "Enterprise ERP (Financial & Supply Chain)",
                    "This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on SAP ERP scenarios that require temporal reasoning and supply-chain awareness.");

            // Scenario 1: Project Funding Network Analysis
            runScenario(writer,
                    "1. Cross-Project Resource Allocation",
                    "Find all cost centers and departments involved in funding overrun projects (requires Project[STATUS=OVERRUN]→FUNDED_BY→Department→HAS_COST_CENTER→CostCenter to discover organizational funding structure and budget exposure across Engineering).",
                    "Find all cost centers funding projects with budget overruns",
                    "Department", "FUNDED_BY", "HAS_COST_CENTER", "USED_IN");

            // Scenario 2: Cross-Department Financial Contagion
            runScenario(writer,
                    "2. Cost Center Contagion Analysis",
                    "Find cost centers at risk due to shared vendor dependencies: Department1→CostCenter1→Vendor→Component→UsedBy→Project→FundedBy→Department2→HasOverrun (requires detecting risk propagation across department boundaries via shared vendors).",
                    "Find departments at financial risk via shared vendor dependencies with overrun departments",
                    "Department", "HAS_COST_CENTER", "PAYS", "SUPPLIES", "USED_IN", "FUNDED_BY", "HAS_OVERRUN");

            logger.info("SAP Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
