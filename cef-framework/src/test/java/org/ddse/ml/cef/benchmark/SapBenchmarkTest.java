package org.ddse.ml.cef.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@DisplayName("Benchmark: SAP Knowledge Model")
class SapBenchmarkTest extends BenchmarkBase {

    private static final String REPORT_FILE = "SAP_BENCHMARK_REPORT.md";

    @BeforeEach
    void setup() throws IOException {
        // Load SAP Data
        SapDataParser parser = new SapDataParser(graphStore, csvParser);
        parser.parseAndLoad();
    }

    @Test
    @DisplayName("Generate SAP Benchmark Report")
    void generateSapBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "SAP Knowledge Model Benchmark", "Enterprise ERP (Financial & Supply Chain)",
                    "This report demonstrates the Knowledge Model's ability to parse raw SAP table dumps (CSV) and answer complex, temporal queries that Vector Search cannot handle.");

            // Scenario 1: Financial GL Analyzer
            runScenario(writer,
                    "Scenario 1: Shadow IT Detection",
                    "Analyze the 'Software Subscription' spend trend for the Engineering department over the last 4 quarters. Flag any vendors with increasing costs that do not have a corresponding budget entry.",
                    "Analyze spend trend for Engineering department and flag suspicious vendors",
                    "Expected: Vendors with increasing costs in Engineering department without matching budget entries");

            // Scenario 2: Supply Chain Impact
            runScenario(writer,
                    "Scenario 2: Typhoon Impact on Holiday Laptop Delivery",
                    "A Typhoon has hit Taiwan for 3 days. Visualize the impact on the 'Holiday Laptop' delivery schedule.",
                    "Impact of Typhoon in Taiwan on Holiday Laptop delivery",
                    "Expected: Delayed deliveries from Taiwan-based suppliers, propagated through supply chain to Holiday Laptop product");

            logger.info("SAP Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
