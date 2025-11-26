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
        // Reset shared stores loaded by MedicalDataTestBase so the SAP dataset stands alone
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

            // Scenario 1: Shadow IT Detection
            runScenario(writer,
                "1. Shadow IT Detection",
                "Analyze the 'Software Subscription' spend trend for the Engineering department over the last 4 quarters. Flag any vendors with increasing costs that do not have a corresponding budget entry.",
                "Analyze spend trend for Engineering department and flag suspicious vendors",
                "CostCenter", "INCURRED_BY", "PAID_TO");

            // Scenario 2: Typhoon Impact on Holiday Laptop Delivery
            runScenario(writer,
                "2. Typhoon Impact: Holiday Laptop Delivery",
                "A Typhoon has hit Taiwan for 3 days. Visualize the impact on the 'Holiday Laptop' delivery schedule.",
                "Impact of Typhoon in Taiwan on Holiday Laptop delivery",
                "Material", "COMPOSED_OF", "SUPPLIED_BY", "LOCATED_IN", "AFFECTS_LOCATION");

            logger.info("SAP Benchmark report generated at: " + REPORT_FILE);
        }
        }
}
