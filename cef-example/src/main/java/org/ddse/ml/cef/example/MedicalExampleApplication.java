package org.ddse.ml.cef.example;

import org.ddse.ml.cef.domain.*;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.mcp.McpContextTool;
import org.ddse.ml.cef.retriever.RetrievalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * CEF Medical Example Application
 * Demonstrates domain-agnostic context engineering with medical domain.
 * 
 * Shows:
 * 1. Relation type registration
 * 2. Node/Edge/Chunk indexing
 * 3. Hybrid retrieval (semantic + graph)
 * 4. MCP tool invocation
 *
 * @author mrmanna
 */
@SpringBootApplication(scanBasePackages = {
        "org.ddse.ml.cef", // Framework
        "org.ddse.ml.cef.example" // Example
})
public class MedicalExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(MedicalExampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MedicalExampleApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(KnowledgeIndexer indexer, McpContextTool mcpTool) {
        return args -> {
            log.info("=== CEF Medical Domain Example ===\n");

            // Step 1: Register relation types (domain-specific)
            log.info("Step 1: Registering medical relation types...");
            List<RelationType> relationTypes = List.of(
                    new RelationType("TREATS", "Doctor", "Patient", RelationSemantics.CAUSAL, true),
                    new RelationType("PRESCRIBES", "Doctor", "Medication", RelationSemantics.CAUSAL, true),
                    new RelationType("DIAGNOSED_WITH", "Patient", "Disease", RelationSemantics.CAUSAL, true),
                    new RelationType("HAS_SYMPTOM", "Patient", "Symptom", RelationSemantics.ASSOCIATIVE, true));

            indexer.initialize(relationTypes).block();
            log.info("✓ Registered {} relation types\n", relationTypes.size());

            // Step 2: Index nodes (entities)
            log.info("Step 2: Indexing medical entities...");
            Node drSmith = new Node(null, "Doctor",
                    Map.of("name", "Dr. Smith", "specialty", "Cardiology"),
                    "Dr. Smith is a cardiologist with 15 years of experience.");

            Node johnDoe = new Node(null, "Patient",
                    Map.of("name", "John Doe", "age", 65),
                    "John Doe is a 65-year-old patient with heart disease history.");

            Node heartDisease = new Node(null, "Disease",
                    Map.of("name", "Coronary Artery Disease", "icd10", "I25.1"),
                    "Coronary Artery Disease affects coronary arteries.");

            Node aspirin = new Node(null, "Medication",
                    Map.of("name", "Aspirin", "dosage", "81mg daily"),
                    "Aspirin prevents blood clots and reduces heart attack risk.");

            List<Node> nodes = indexer.indexNodes(List.of(drSmith, johnDoe, heartDisease, aspirin))
                    .collectList().block();
            log.info("✓ Indexed {} nodes\n", nodes.size());

            // Step 3: Index edges (relationships)
            log.info("Step 3: Indexing relationships...");
            Edge treats = new Edge(null, "TREATS", drSmith.getId(), johnDoe.getId(), null, 1.0);
            Edge diagnosed = new Edge(null, "DIAGNOSED_WITH", johnDoe.getId(), heartDisease.getId(), null, 1.0);
            Edge prescribes = new Edge(null, "PRESCRIBES", drSmith.getId(), aspirin.getId(), null, 1.0);

            List<Edge> edges = indexer.indexEdges(List.of(treats, diagnosed, prescribes))
                    .collectList().block();
            log.info("✓ Indexed {} edges\n", edges.size());

            // Step 4: Index text chunks (documentation)
            log.info("Step 4: Indexing text chunks with auto-embeddings...");
            Chunk chunk1 = new Chunk(null,
                    "Patient presented with chest pain and shortness of breath. " +
                            "EKG showed ischemia. Diagnosed with Coronary Artery Disease.",
                    null, johnDoe.getId(), Map.of("source", "clinical_notes"));

            Chunk chunk2 = new Chunk(null,
                    "Treatment: Low-dose Aspirin 81mg daily to prevent clots. " +
                            "Follow up in 2 weeks for stress test.",
                    null, drSmith.getId(), Map.of("source", "treatment_plan"));

            List<Chunk> chunks = indexer.indexChunks(List.of(chunk1, chunk2))
                    .collectList().block();
            log.info("✓ Indexed {} chunks with embeddings\n", chunks.size());

            // Step 5: MCP Tool - Hybrid retrieval
            log.info("Step 5: Invoking MCP Tool (Hybrid Retrieval)...");
            RetrievalRequest request = new RetrievalRequest("What is the treatment for heart disease?");
            request.setTopK(5);
            request.setGraphDepth(2);

            String context = mcpTool.invoke(request).block();
            log.info("\n{}\n", context);

            log.info("=== Example Complete ===");
        };
    }
}
