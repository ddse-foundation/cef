package org.ddse.ml.cef.benchmark.scenario;

import java.util.Arrays;
import java.util.List;

/**
 * Defines the medical benchmark scenarios.
 * Each scenario tests a different multi-hop reasoning pattern.
 * 
 * <h3>Design Principles for Knowledge Model Superiority:</h3>
 * <ul>
 *   <li>Queries should be semantically DISTANT from answers (vector search fails)</li>
 *   <li>Answers require 3+ hop graph traversal</li>
 *   <li>Entity relationships are NOT mentioned in chunk text</li>
 *   <li>Structural patterns are key to finding relevant context</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
public class MedicalScenarios {

    /**
     * Scenario 1: Hidden Drug Interaction Chain (4-hop)
     * 
     * Vector search FAILS because: Query mentions "heart surgery prep" but answer
     * requires finding patients through: Surgery→Requires→Medication→Interacts→OtherMed→PrescribedTo→Patient
     * The chunks about patients don't mention surgery preparation.
     */
    public static final Scenario HIDDEN_DRUG_INTERACTION = new Scenario(
            "Hidden Drug Interaction Chain",
            "Find patients at risk for heart surgery complications due to undiscovered medication interactions. " +
                    "Requires: Surgery→PreOp_Medication→INTERACTS_WITH→Patient_Medication→Patient path",
            "Which patients scheduled for cardiac procedures have medications that interact with standard pre-op drugs",
            new String[]{"Patient", "SCHEDULED_FOR", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "INTERACTS_WITH"}
    );

    /**
     * Scenario 2: Transitive Infection Exposure (5-hop)
     * 
     * Vector search FAILS because: Query asks about "infection exposure" but the
     * path is: Patient→TreatedBy→Doctor→TreatedBy→InfectedPatient→HasCondition→InfectiousDisease
     * No chunk directly links doctors to infection transmission risk.
     */
    public static final Scenario TRANSITIVE_INFECTION = new Scenario(
            "Transitive Infection Exposure Risk",
            "Find patients potentially exposed to infectious diseases through shared healthcare providers. " +
                    "Requires traversing: Patient→TREATED_BY→Doctor→TREATED_BY→InfectedPatient→HAS_CONDITION→InfectiousDisease",
            "Identify patients who share doctors with patients diagnosed with communicable diseases",
            new String[]{"Patient", "TREATED_BY", "HAS_CONDITION"}
    );

    /**
     * Scenario 3: Polypharmacy Cascade Risk (6-hop)
     * 
     * Vector search FAILS because: Query is about "elderly fall risk" but answer
     * requires: Patient[age>65]→Prescribed→Med1→CausesSideEffect→Dizziness AND
     *           Patient→Prescribed→Med2→CausesSideEffect→Drowsiness AND
     *           Patient→HasCondition→Osteoporosis
     * No single chunk contains all these relationships.
     */
    public static final Scenario POLYPHARMACY_CASCADE = new Scenario(
            "Polypharmacy Cascade Risk",
            "Find elderly patients at high fall risk due to multiple medication side effects combined with bone conditions. " +
                    "Requires aggregating: Patient→PRESCRIBED→Meds→CAUSES_SIDE_EFFECT→[Dizziness,Drowsiness] AND Patient→HAS_CONDITION→Osteoporosis",
            "Which senior patients on multiple medications are at elevated fall risk due to combined drug side effects",
            new String[]{"Patient", "PRESCRIBED_MEDICATION", "CAUSES_SIDE_EFFECT", "HAS_CONDITION"}
    );

    /**
     * Scenario 4: Treatment Protocol Gap (4-hop)
     * 
     * Vector search FAILS because: Query asks about "protocol compliance" but
     * requires: Condition→StandardTreatment→Medication AND Patient→HasCondition→Condition AND
     *           Patient→NOT_Prescribed→Medication (gap detection via graph difference)
     */
    public static final Scenario TREATMENT_PROTOCOL_GAP = new Scenario(
            "Treatment Protocol Gap Detection",
            "Find patients with conditions who are NOT receiving standard-of-care medications. " +
                    "Requires: Condition→STANDARD_TREATMENT→Medication comparison with Patient→PRESCRIBED→Medications",
            "Identify patients with diabetes who are missing recommended first-line treatment medications",
            new String[]{"Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "STANDARD_TREATMENT"}
    );

    /**
     * Scenario 5: Cross-Specialty Contraindication (5-hop)
     * 
     * Vector search FAILS because: Query mentions specialty coordination but answer
     * requires: Patient→TreatedBy→Cardiologist→Prescribed→Med1 AND
     *           Patient→TreatedBy→Neurologist→Prescribed→Med2 AND Med1→CONTRAINDICATED_WITH→Med2
     * Different specialists' prescriptions must be cross-referenced via graph.
     */
    public static final Scenario CROSS_SPECIALTY_CONTRAINDICATION = new Scenario(
            "Cross-Specialty Contraindication",
            "Find patients whose medications from different specialists are contraindicated with each other. " +
                    "Requires: Patient→TREATED_BY→Doctor[specialty=A]→PRESCRIBED→Med1 CROSS " +
                    "Patient→TREATED_BY→Doctor[specialty=B]→PRESCRIBED→Med2 WHERE Med1→CONTRAINDICATED→Med2",
            "Which patients have potentially dangerous drug combinations from different specialist prescriptions",
            new String[]{"Patient", "TREATED_BY", "PRESCRIBED_MEDICATION", "CONTRAINDICATED_FOR"}
    );

    /**
     * All medical benchmark scenarios.
     */
    public static final List<Scenario> ALL = Arrays.asList(
            HIDDEN_DRUG_INTERACTION,
            TRANSITIVE_INFECTION,
            POLYPHARMACY_CASCADE,
            TREATMENT_PROTOCOL_GAP,
            CROSS_SPECIALTY_CONTRAINDICATION
    );

    /**
     * Scenario definition.
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
