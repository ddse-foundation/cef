package org.ddse.ml.cef.benchmark;

import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles({ "vllm-integration", "duckdb" })
class DebugGraphTest extends BenchmarkBase {

    @Autowired
    private KnowledgeRetriever retriever;

    @BeforeEach
    void setup() throws IOException {
        // Load data using the method from BenchmarkBase (if accessible) or copy it
        // BenchmarkBase doesn't have loadBenchmarkData public/protected in the version
        // I read?
        // Wait, MedicalBenchmarkTest2 had loadBenchmarkData. BenchmarkBase did not.
        // I need to copy loadBenchmarkData logic or make it available.
        // I'll copy it here for safety.
        loadBenchmarkData();
    }

    private void loadBenchmarkData() throws IOException {
        // Copy of loadBenchmarkData from MedicalBenchmarkTest2
        // Need to import classes
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(
                new org.springframework.core.io.ClassPathResource("medical_benchmark_data.json").getInputStream());

        for (com.fasterxml.jackson.databind.JsonNode nodeJson : root.get("nodes")) {
            java.util.UUID id = java.util.UUID.fromString(nodeJson.get("id").asText());
            String label = nodeJson.get("label").asText();
            Map<String, Object> props = objectMapper.convertValue(nodeJson.get("properties"), Map.class);
            Node node = new Node(id, label, props, null);
            indexer.indexNode(node).block();
        }

        for (com.fasterxml.jackson.databind.JsonNode edgeJson : root.get("edges")) {
            java.util.UUID id = java.util.UUID.fromString(edgeJson.get("id").asText());
            java.util.UUID sourceId = java.util.UUID.fromString(edgeJson.get("sourceNodeId").asText());
            java.util.UUID targetId = java.util.UUID.fromString(edgeJson.get("targetNodeId").asText());
            String type = edgeJson.get("relationType").asText();
            Map<String, Object> props = objectMapper.convertValue(edgeJson.get("properties"), Map.class);
            org.ddse.ml.cef.domain.Edge edge = new org.ddse.ml.cef.domain.Edge(id, type, sourceId, targetId, props,
                    1.0);
            indexer.indexEdge(edge).block();
        }

        // Chunks not strictly needed for graph test but good to have
    }

    @Test
    @DisplayName("Debug: Verify Smart Truncation for Hub Nodes")
    void verifySmartTruncation() {
        // PT-10004 has Rheumatoid Arthritis (Hub) and Albuterol
        // Query: What treatments... (relationTypes=null)
        // This should fetch PT-10004, Rheumatoid Arthritis, Albuterol, and maybe some
        // other patients
        // But with smart truncation, it should prioritize PT-10004 and its direct
        // neighbors (Condition, Meds)
        // over neighbors-of-neighbors (other patients).

        GraphQuery graphQuery = new GraphQuery(
                List.of(new ResolutionTarget("PT-10004", "Patient", Map.of())),
                new TraversalHint(2, null, null));

        RetrievalRequest request = RetrievalRequest.builder()
                .query("What treatments is patient PT-10004 receiving for Rheumatoid Arthritis?")
                .topK(5)
                .maxGraphNodes(20)
                .graphQuery(graphQuery)
                .build();

        Mono<RetrievalResult> resultMono = retriever.retrieve(request);

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    System.out.println("Debug Result: " + result.getNodes().size() + " nodes");
                    result.getNodes().forEach(n -> System.out.println(" - " + n.getLabel() + ": " + n.getProperties()));

                    boolean hasPatient = result.getNodes().stream()
                            .anyMatch(n -> "Patient".equals(n.getLabel())
                                    && "PT-10004".equals(n.getProperties().get("patient_id")));

                    boolean hasCondition = result.getNodes().stream()
                            .anyMatch(n -> "Condition".equals(n.getLabel())
                                    && "Rheumatoid Arthritis".equals(n.getProperties().get("name")));

                    boolean hasMedication = result.getNodes().stream()
                            .anyMatch(n -> "Medication".equals(n.getLabel())
                                    && "Albuterol".equals(n.getProperties().get("name")));

                    if (!hasPatient)
                        System.err.println("FAILED: Patient PT-10004 missing!");
                    if (!hasCondition)
                        System.err.println("FAILED: Condition Rheumatoid Arthritis missing!");
                    if (!hasMedication)
                        System.err.println("FAILED: Medication Albuterol missing!");

                    assert hasPatient;
                    assert hasCondition;
                    // Albuterol is depth 1 from Patient (PRESCRIBED_MEDICATION). It should be
                    // there.
                    assert hasMedication;
                })
                .verifyComplete();
    }
}
