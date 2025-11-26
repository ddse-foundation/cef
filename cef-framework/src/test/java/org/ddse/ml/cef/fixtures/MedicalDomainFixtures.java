package org.ddse.ml.cef.fixtures;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-configured medical domain test fixtures.
 * Creates realistic medical knowledge graphs for integration tests.
 * 
 * <p>
 * Scenarios:
 * <ul>
 * <li>Diabetes scenario: Patient, Doctor, Conditions, Medications, Clinical
 * guidelines</li>
 * <li>Cardiology scenario: Heart disease, treatments, procedures</li>
 * </ul>
 * 
 * @author mrmanna
 */
@Component
public class MedicalDomainFixtures {

    /**
     * Creates a complete diabetes treatment scenario.
     * 
     * <p>
     * Structure:
     * 
     * <pre>
     * Patient (John Smith, P12345, age 67)
     *   ├─[TREATED_BY]─→ Doctor (Dr. Sarah Johnson, Endocrinology)
     *   ├─[HAS_CONDITION]─→ Condition (Type 2 Diabetes, E11)
     *   │   └─[PRESCRIBED_MEDICATION]─→ Medication (Metformin, 500mg)
     *   └─[HAS_CONDITION]─→ Condition (Hypertension, I10)
     *       └─[PRESCRIBED_MEDICATION]─→ Medication (Lisinopril, 10mg)
     * 
     * Chunks (with real embeddings):
     *   - Diabetes management guidelines
     *   - Hypertension treatment for diabetic patients
     * </pre>
     * 
     * @param embeddingModel Real Ollama embedding model for generating embeddings
     * @return Complete medical scenario with nodes, edges, and chunks
     */
    public MedicalScenario createDiabetesScenario(EmbeddingModel embeddingModel) {
        // Nodes
        Node patient = TestDataBuilder.node()
                .label("Patient")
                .property("patientId", "P12345")
                .property("name", "John Smith")
                .property("age", 67)
                .property("gender", "Male")
                .vectorizableContent("Patient John Smith, 67 years old male, diabetic with hypertension")
                .build();

        Node doctor = TestDataBuilder.node()
                .label("Doctor")
                .property("doctorId", "D001")
                .property("name", "Dr. Sarah Johnson")
                .property("specialty", "Endocrinology")
                .property("yearsExperience", 15)
                .vectorizableContent("Dr. Sarah Johnson, board-certified endocrinologist")
                .build();

        Node diabetes = TestDataBuilder.node()
                .label("Condition")
                .property("conditionId", "C001")
                .property("name", "Type 2 Diabetes")
                .property("icd10", "E11")
                .property("severity", "Moderate")
                .vectorizableContent("Type 2 diabetes mellitus with insulin resistance and impaired glucose tolerance")
                .build();

        Node hypertension = TestDataBuilder.node()
                .label("Condition")
                .property("conditionId", "C002")
                .property("name", "Hypertension")
                .property("icd10", "I10")
                .property("severity", "Stage 1")
                .vectorizableContent("Essential hypertension, stage 1, well-controlled")
                .build();

        Node metformin = TestDataBuilder.node()
                .label("Medication")
                .property("medicationId", "M001")
                .property("name", "Metformin")
                .property("genericName", "Metformin Hydrochloride")
                .property("dosage", "500mg")
                .property("frequency", "twice daily")
                .vectorizableContent("Metformin 500mg, oral antidiabetic medication for type 2 diabetes")
                .build();

        Node lisinopril = TestDataBuilder.node()
                .label("Medication")
                .property("medicationId", "M002")
                .property("name", "Lisinopril")
                .property("genericName", "Lisinopril")
                .property("dosage", "10mg")
                .property("frequency", "once daily")
                .vectorizableContent("Lisinopril 10mg, ACE inhibitor for hypertension management")
                .build();

        // Edges
        Edge treatedBy = TestDataBuilder.edge()
                .from(patient.getId())
                .to(doctor.getId())
                .relationType("TREATED_BY")
                .property("since", "2020-01-15")
                .build();

        Edge hasDiabetes = TestDataBuilder.edge()
                .from(patient.getId())
                .to(diabetes.getId())
                .relationType("HAS_CONDITION")
                .property("diagnosedDate", "2018-06-20")
                .property("status", "Active")
                .build();

        Edge hasHypertension = TestDataBuilder.edge()
                .from(patient.getId())
                .to(hypertension.getId())
                .relationType("HAS_CONDITION")
                .property("diagnosedDate", "2019-03-10")
                .property("status", "Active")
                .build();

        Edge prescribedMetformin = TestDataBuilder.edge()
                .from(diabetes.getId())
                .to(metformin.getId())
                .relationType("PRESCRIBED_MEDICATION")
                .property("prescribedDate", "2018-06-20")
                .property("prescribedBy", "D001")
                .build();

        Edge prescribedLisinopril = TestDataBuilder.edge()
                .from(hypertension.getId())
                .to(lisinopril.getId())
                .relationType("PRESCRIBED_MEDICATION")
                .property("prescribedDate", "2019-03-10")
                .property("prescribedBy", "D001")
                .build();

        // Chunks with real embeddings
        Chunk diabetesGuideline = TestDataBuilder.chunk()
                .content(
                        "Type 2 diabetes management requires comprehensive approach including blood glucose monitoring, "
                                +
                                "dietary modifications with carbohydrate control, regular physical activity, and pharmacological "
                                +
                                "intervention. Metformin is the first-line medication, working by improving insulin sensitivity "
                                +
                                "and reducing hepatic glucose production. Target HbA1c should be below 7% for most patients. "
                                +
                                "Regular monitoring of kidney function and vitamin B12 levels is recommended for patients on Metformin.")
                .linkedTo(diabetes.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "clinical_guidelines.pdf")
                .metadata("page", 42)
                .metadata("lastUpdated", "2024-01-15")
                .build();

        Chunk hypertensionGuideline = TestDataBuilder.chunk()
                .content(
                        "Hypertension management in diabetic patients requires careful consideration as these patients "
                                +
                                "have significantly increased cardiovascular risk. ACE inhibitors like Lisinopril are preferred "
                                +
                                "first-line agents due to renal protective effects. Target blood pressure should be below 130/80 mmHg. "
                                +
                                "ACE inhibitors reduce proteinuria and slow progression of diabetic nephropathy. Regular monitoring "
                                +
                                "of serum potassium and creatinine is essential. Lifestyle modifications including sodium restriction "
                                +
                                "and weight management are crucial adjuncts to pharmacotherapy.")
                .linkedTo(hypertension.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "cardiology_handbook.pdf")
                .metadata("page", 156)
                .metadata("lastUpdated", "2024-02-20")
                .build();

        Chunk metforminInfo = TestDataBuilder.chunk()
                .content("Metformin mechanism of action involves activation of AMP-activated protein kinase (AMPK), " +
                        "leading to decreased hepatic gluconeogenesis and increased peripheral glucose uptake. " +
                        "Common side effects include gastrointestinal disturbances which can be minimized by taking " +
                        "with meals and gradual dose titration. Contraindicated in severe renal impairment (eGFR < 30 mL/min). "
                        +
                        "Rare but serious complication is lactic acidosis, particularly in patients with hypoxic conditions.")
                .linkedTo(metformin.getId())
                .withRealEmbedding(embeddingModel)
                .metadata("source", "pharmacology_database")
                .metadata("drug_class", "Biguanide")
                .build();

        return new MedicalScenario(
                List.of(patient, doctor, diabetes, hypertension, metformin, lisinopril),
                List.of(treatedBy, hasDiabetes, hasHypertension, prescribedMetformin, prescribedLisinopril),
                List.of(diabetesGuideline, hypertensionGuideline, metforminInfo),
                patient,
                doctor);
    }

    /**
     * Container for a complete medical scenario.
     */
    public record MedicalScenario(
            List<Node> nodes,
            List<Edge> edges,
            List<Chunk> chunks,
            Node primaryPatient,
            Node primaryDoctor) {
    }
}
