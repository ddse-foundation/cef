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

@DisplayName("Benchmark: Medical Knowledge Model")
class MedicalBenchmarkTest extends BenchmarkBase {

    private static final String REPORT_FILE = "BENCHMARK_REPORT.md";

    @BeforeEach
    void setup() throws IOException {
        loadBenchmarkData();
    }

    @Test
    @DisplayName("Generate Medical Benchmark Report")
    void generateBenchmarkReport() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(REPORT_FILE))) {
            writeHeader(writer, "Knowledge Model vs. Vector Retrieval Benchmark", "Medical (Clinical Decision Support)",
                    "This report compares the effectiveness of **Vector-Only (Naive RAG)** versus **Knowledge Model (Graph RAG)** retrieval strategies on complex scenarios requiring structural reasoning.");

            // Scenario 1: Safety Check (Contraindications)
            runScenario(writer,
                    "1. Safety Check: Contraindicated Medications",
                    "Identify patients taking medications that are contraindicated for their specific conditions.",
                    "Find patients with conditions taking contraindicated medications",
                    "Patient", "HAS_CONDITION", "PRESCRIBED_MEDICATION", "CONTRAINDICATED_FOR");

            // Scenario 2: Behavioral Risk (Smokers with Asthma)
            runScenario(writer,
                    "2. Behavioral Risk: Smokers with Asthma",
                    "Find patients with 'Bronchial Asthma' who are current smokers.",
                    "Find patients with Bronchial Asthma who smoke",
                    "Patient", "HAS_CONDITION");

            // Scenario 3: Root Cause (Side Effects)
            runScenario(writer,
                    "3. Root Cause Analysis",
                    "List side effects reported by patients taking 'Prednisone' who also have 'Type 2 Diabetes'.",
                    "Side effects of Prednisone for Diabetes patients",
                    "Medication", "PRESCRIBED_MEDICATION", "HAS_CONDITION");

            logger.info("Benchmark report generated at: " + REPORT_FILE);
        }
    }

    private void loadBenchmarkData() throws IOException {
        // Load JSON from file
        JsonNode root = objectMapper.readTree(new ClassPathResource("medical_benchmark_data.json").getInputStream());

        // Use KnowledgeIndexer instead of direct graphStore - follows framework pattern
        // indexer.indexNode/indexEdge already implements dual persistence (DB +
        // InMemory)

        // Load Nodes via KnowledgeIndexer (handles dual persistence + embedding
        // generation)
        for (JsonNode nodeJson : root.get("nodes")) {
            UUID id = UUID.fromString(nodeJson.get("id").asText());
            String label = nodeJson.get("label").asText();
            Map<String, Object> props = objectMapper.convertValue(nodeJson.get("properties"), Map.class);
            Node node = new Node(id, label, props, null);
            indexer.indexNode(node).block(); // Framework handles dual persistence
        }

        // Load Edges via KnowledgeIndexer (handles dual persistence)
        for (JsonNode edgeJson : root.get("edges")) {
            UUID id = UUID.fromString(edgeJson.get("id").asText());
            UUID sourceId = UUID.fromString(edgeJson.get("sourceNodeId").asText());
            UUID targetId = UUID.fromString(edgeJson.get("targetNodeId").asText());
            String type = edgeJson.get("relationType").asText();
            Map<String, Object> props = objectMapper.convertValue(edgeJson.get("properties"), Map.class);
            Edge edge = new Edge(id, type, sourceId, targetId, props, 1.0);
            indexer.indexEdge(edge).block(); // Framework handles dual persistence
        }

        // Load Chunks via KnowledgeIndexer (handles embedding generation + storage)
        for (JsonNode chunkJson : root.get("chunks")) {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.fromString(chunkJson.get("id").asText()));
            chunk.setContent(chunkJson.get("content").asText());
            // Don't manually generate embedding - let indexer handle it
            // chunk.setEmbedding(embeddingModel.embed(chunk.getContent()));

            if (chunkJson.has("linkedNodeId")) {
                chunk.setLinkedNodeId(UUID.fromString(chunkJson.get("linkedNodeId").asText()));
            }
            if (chunkJson.has("metadata")) {
                chunk.setMetadata(objectMapper.convertValue(chunkJson.get("metadata"), Map.class));
            }
            indexer.indexChunk(chunk).block(); // Framework handles embedding + storage
        }

        logger.info("Loaded {} nodes, {} edges, {} chunks via KnowledgeIndexer (dual persistence via framework).",
                root.get("nodes").size(), root.get("edges").size(), root.get("chunks").size());
    }
}
