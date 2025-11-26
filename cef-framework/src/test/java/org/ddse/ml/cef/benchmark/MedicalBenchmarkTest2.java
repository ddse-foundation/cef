package org.ddse.ml.cef.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;

@DisplayName("Benchmark: Medical Knowledge Model (Advanced Scenarios)")
class MedicalBenchmarkTest2 extends BenchmarkBase {

    private static final String REPORT_FILE = "BENCHMARK_REPORT_2.md";

    @BeforeEach
    void setup() throws IOException {
        loadBenchmarkData();
    }

    @Test
    @DisplayName("Generate Medical Benchmark Report 2")
    void generateBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "Knowledge Model vs. Vector Retrieval Benchmark (Advanced)",
                    "Medical (Clinical Decision Support)",
                    "This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning (Multi-hop, Intersection, Aggregation).");

            // Scenario 1: "Patient Zero" Link (Network Hop)
            runScenario(writer,
                    "1. Network Hop: Patient Zero Link",
                    "Find all patients treated by the same doctor as 'PT-10001'.",
                    "Find all patients treated by the same doctor as PT-10001",
                    "Patient", "TREATED_BY");

            // Scenario 2: "Hidden Comorbidity" (Intersection)
            runScenario(writer,
                    "2. Intersection: Condition + Medication",
                    "Find patients diagnosed with 'Rheumatoid Arthritis' who are also prescribed 'Albuterol'.",
                    "Find patients diagnosed with Rheumatoid Arthritis who are also prescribed Albuterol",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION");

            // Scenario 3: "Provider Pattern" (Aggregation)
            runScenario(writer,
                    "3. Aggregation: Provider Pattern",
                    "List doctors who are treating more than one patient with 'Rheumatoid Arthritis'.",
                    "List doctors who are treating more than one patient with Rheumatoid Arthritis",
                    "Patient", "TREATED_BY", "HAS_CONDITION");

            logger.info("Benchmark report generated at: " + REPORT_FILE);
        }
    }

    private void loadBenchmarkData() throws IOException {
        // Load JSON from file
        JsonNode root = objectMapper.readTree(new ClassPathResource("medical_benchmark_data.json").getInputStream());

        // Load Nodes via KnowledgeIndexer
        for (JsonNode nodeJson : root.get("nodes")) {
            UUID id = UUID.fromString(nodeJson.get("id").asText());
            String label = nodeJson.get("label").asText();
            Map<String, Object> props = objectMapper.convertValue(nodeJson.get("properties"), Map.class);
            Node node = new Node(id, label, props, null);
            indexer.indexNode(node).block();
        }

        // Load Edges via KnowledgeIndexer
        for (JsonNode edgeJson : root.get("edges")) {
            UUID id = UUID.fromString(edgeJson.get("id").asText());
            UUID sourceId = UUID.fromString(edgeJson.get("sourceNodeId").asText());
            UUID targetId = UUID.fromString(edgeJson.get("targetNodeId").asText());
            String type = edgeJson.get("relationType").asText();
            Map<String, Object> props = objectMapper.convertValue(edgeJson.get("properties"), Map.class);
            Edge edge = new Edge(id, type, sourceId, targetId, props, 1.0);
            indexer.indexEdge(edge).block();
        }

        // Load Chunks via KnowledgeIndexer
        for (JsonNode chunkJson : root.get("chunks")) {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(chunkJson.get("id").asText()));
            chunk.setContent(chunkJson.get("content").asText());

            if (chunkJson.has("linkedNodeId")) {
                chunk.setLinkedNodeId(UUID.fromString(chunkJson.get("linkedNodeId").asText()));
            }
            if (chunkJson.has("metadata")) {
                chunk.setMetadata(objectMapper.convertValue(chunkJson.get("metadata"), Map.class));
            }
            indexer.indexChunk(chunk).block();
        }

        logger.info("Loaded {} nodes, {} edges, {} chunks via KnowledgeIndexer (dual persistence via framework).",
                root.get("nodes").size(), root.get("edges").size(), root.get("chunks").size());
    }
}
