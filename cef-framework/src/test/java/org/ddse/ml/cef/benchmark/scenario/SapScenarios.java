package org.ddse.ml.cef.benchmark.scenario;

import java.util.Arrays;
import java.util.List;

/**
 * Defines the SAP ERP benchmark scenarios.
 * Each scenario tests enterprise graph traversal patterns.
 * 
 * <h3>Design Principles for Knowledge Model Superiority:</h3>
 * <ul>
 *   <li>Queries use business language, answers require ERP structure traversal</li>
 *   <li>Supply chain impacts require 4-6 hop BOM/Vendor traversals</li>
 *   <li>Financial exposure requires Cost Center→Department→Project chains</li>
 *   <li>Risk propagation spans multiple organizational boundaries</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
public class SapScenarios {

    /**
     * Scenario 1: Supply Chain Disruption Impact (6-hop)
     * 
     * Vector search FAILS because: Query asks about "delivery delays" but answer
     * requires: Event→AffectsLocation→Vendor→Supplies→Component→ComposedOf→Product→OrderedIn→CustomerOrder
     * No chunk directly links typhoons to customer orders.
     */
    public static final Scenario SUPPLY_CHAIN_DISRUPTION = new Scenario(
            "Supply Chain Disruption Impact",
            "Find customer orders at risk due to natural disaster affecting component suppliers. " +
                    "Requires: Event→AFFECTS_LOCATION→Location→LOCATED_IN→Vendor→SUPPLIES→Material→COMPOSED_OF→Product→ORDERS",
            "Which customer orders will be delayed due to the Taiwan typhoon affecting our component suppliers",
            new String[]{"Event", "AFFECTS_LOCATION", "LOCATED_IN", "SUPPLIES", "COMPOSED_OF", "ORDERS"}
    );

    /**
     * Scenario 2: Budget Contagion via Shared Vendors (5-hop)
     * 
     * Vector search FAILS because: Query asks about "budget risk" but answer
     * requires: Department[overrun]→HasCostCenter→CostCenter→Pays→Vendor→Supplies→Material→UsedIn→Project→FundedBy→Department2
     * Financial exposure propagates through vendor relationships.
     */
    public static final Scenario BUDGET_CONTAGION = new Scenario(
            "Budget Contagion via Shared Vendors",
            "Find departments at financial risk due to shared vendor dependencies with overrun departments. " +
                    "Requires: Dept[overrun]→HAS_COST_CENTER→CC→PAYS→Vendor→SUPPLIES→Material→USED_IN→Project→FUNDED_BY→Dept2",
            "Which departments share vendors with the Engineering department that has budget overruns",
            new String[]{"Department", "HAS_COST_CENTER", "PAYS", "SUPPLIES", "USED_IN", "FUNDED_BY", "HAS_OVERRUN"}
    );

    /**
     * Scenario 3: Critical Component Single Sourcing (4-hop)
     * 
     * Vector search FAILS because: Query asks about "supply risk" but answer
     * requires: Product→ComposedOf→Component→SuppliedBy→Vendor (COUNT=1) identification
     * Risk assessment needs graph aggregation, not text matching.
     */
    public static final Scenario SINGLE_SOURCE_RISK = new Scenario(
            "Critical Component Single Sourcing Risk",
            "Find products with components that have only one supplier (single point of failure). " +
                    "Requires: Product→COMPOSED_OF→Material→SUPPLIED_BY→Vendor with cardinality check",
            "Which products have critical components sourced from only one vendor creating supply chain vulnerability",
            new String[]{"Product", "COMPOSED_OF", "SUPPLIED_BY"}
    );

    /**
     * Scenario 4: Project Overrun Root Cause (5-hop)
     * 
     * Vector search FAILS because: Query asks about "overrun causes" but answer
     * requires: Project[overrun]→UsedIn→Material→SuppliedBy→Vendor→LocatedIn→Location→AffectedBy→Event
     * Root cause analysis requires reverse traversal through supply chain.
     */
    public static final Scenario PROJECT_OVERRUN_ROOT_CAUSE = new Scenario(
            "Project Overrun Root Cause Analysis",
            "Find external factors (events, vendor issues) contributing to project budget overruns. " +
                    "Requires: Project[OVERRUN]←USED_IN←Material→SUPPLIED_BY→Vendor→LOCATED_IN→Location←AFFECTS←Event",
            "What external events or vendor issues are causing the Holiday Laptop project to run over budget",
            new String[]{"Project", "USED_IN", "SUPPLIED_BY", "LOCATED_IN", "AFFECTS_LOCATION"}
    );

    /**
     * Scenario 5: Cross-Department Invoice Anomaly (4-hop)
     * 
     * Vector search FAILS because: Query asks about "invoice anomalies" but answer
     * requires: Invoice→PaidTo→Vendor AND Invoice→IncurredBy→CostCenter→BelongsTo→Department
     * where vendor is NOT in department's approved vendor list from Budget.
     */
    public static final Scenario INVOICE_ANOMALY = new Scenario(
            "Cross-Department Invoice Anomaly Detection",
            "Find invoices paid to vendors not in the department's approved vendor list. " +
                    "Requires: Invoice→PAID_TO→Vendor comparison with Department→HAS_BUDGET→Budget[approved_vendors]",
            "Which invoices were paid to vendors outside the department's pre-approved supplier list",
            new String[]{"Invoice", "PAID_TO", "INCURRED_BY", "BELONGS_TO", "HAS_BUDGET"}
    );

    /**
     * All SAP benchmark scenarios.
     */
    public static final List<Scenario> ALL = Arrays.asList(
            SUPPLY_CHAIN_DISRUPTION,
            BUDGET_CONTAGION,
            SINGLE_SOURCE_RISK,
            PROJECT_OVERRUN_ROOT_CAUSE,
            INVOICE_ANOMALY
    );

    /**
     * Scenario definition (shared with MedicalScenarios).
     */
    public static class Scenario {
        private final String name;
        private final String description;
        private final String query;
        private final String[] graphHints;

        public Scenario(String name, String description, String query, String[] graphHints) {
            this.name = name;
            this.description = description;
            this.query = query;
            this.graphHints = graphHints;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getQuery() {
            return query;
        }

        public String[] getGraphHints() {
            return graphHints;
        }

        /**
         * Build pattern description for reporting.
         */
        public String getPatternDescription() {
            if (graphHints == null || graphHints.length < 2) {
                return "Vector-only (no patterns)";
            }
            StringBuilder sb = new StringBuilder(graphHints[0]);
            for (int i = 1; i < graphHints.length; i++) {
                sb.append("→").append(graphHints[i]).append("→*");
            }
            return sb.toString();
        }
    }
}
