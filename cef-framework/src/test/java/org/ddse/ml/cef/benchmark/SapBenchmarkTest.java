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

            // Scenario 1: Multi-Hop Budget Compliance Violation
            runScenario(writer,
                    "1. Shadow IT Budget Leak Detection",
                    "Find invoices paid to vendors NOT in approved budget, where the vendor supplies components used by Engineering, AND those components are used in projects with cost overruns (requires: Invoice→Vendor→Component→Project→Department→Budget→ApprovedVendors).",
                    "Find Engineering invoices to unapproved vendors supplying overrun projects",
                    "Invoice", "PAID_TO", "SUPPLIES", "USED_IN", "BELONGS_TO", "HAS_BUDGET");

            // Scenario 2: Cascading Supply Chain Impact
            runScenario(writer,
                    "2. Transitive Supply Chain Disruption",
                    "Find ALL products affected by Taiwan typhoon through multi-tier supply chain: Typhoon→Location→Vendor→Component→SubAssembly→Product→CustomerOrder (requires 6-hop traversal to find downstream impact).",
                    "Find all customer orders affected by Taiwan typhoon via supply chain cascade",
                    "Location", "AFFECTED_BY", "LOCATED_IN", "SUPPLIED_BY", "COMPONENT_OF", "USED_IN", "ORDERED_IN");

            // Scenario 3: Hidden Vendor Concentration Risk
            runScenario(writer,
                    "3. Transitive Vendor Single Point of Failure",
                    "Identify products dependent on Taiwan Semi through hidden chains: Product→Component→Subcomponent→RawMaterial→Supplier→Vendor, where Vendor=TSMC but product documentation only lists Component (requires 5-hop to discover hidden dependency).",
                    "Find products with hidden transitive dependencies on Taiwan Semi",
                    "Product", "COMPOSED_OF", "CONTAINS", "MADE_FROM", "SUPPLIED_BY");

            // Scenario 4: Cross-Department Financial Contagion
            runScenario(writer,
                    "4. Cost Center Contagion Analysis",
                    "Find cost centers at risk due to shared vendor dependencies: Department1→CostCenter1→Vendor→Component→UsedBy→Project→FundedBy→Department2→HasOverrun (requires detecting risk propagation across department boundaries via shared vendors).",
                    "Find departments at financial risk via shared vendor dependencies with overrun departments",
                    "Department", "HAS_COST_CENTER", "PAYS", "SUPPLIES", "USED_IN", "FUNDED_BY", "HAS_OVERRUN");

            logger.info("SAP Benchmark report generated at: " + REPORT_FILE);
        }
    }
}
