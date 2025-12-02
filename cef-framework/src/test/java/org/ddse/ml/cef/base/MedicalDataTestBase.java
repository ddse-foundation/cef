package org.ddse.ml.cef.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.indexer.KnowledgeIndexer;
import org.ddse.ml.cef.repository.duckdb.ChunkStore;
import org.ddse.ml.cef.graph.GraphStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Shared base class for all tests using the medical_benchmark_data.json
 * dataset.
 * Provides consistent data loading logic and ensures all tests work on the same
 * canonical medical knowledge graph.
 * 
 * <p>
 * This eliminates code duplication across MedicalBenchmarkTest,
 * MedicalBenchmarkTest2,
 * MedicalDataIntegrationTest, DebugGraphTest, and other medical domain tests.
 * </p>
 * 
 * <p>
 * <b>Usage:</b> Extend this class instead of directly extending BenchmarkBase
 * or
 * setting up your own data loading.
 * </p>
 * 
 * @author mrmanna
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners({
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class
})
public abstract class MedicalDataTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(MedicalDataTestBase.class);
    protected final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    protected GraphStore graphStore;

    @Autowired
    protected ChunkStore chunkStore;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("knowledgeIndexer")
    protected KnowledgeIndexer indexer;

    /**
     * Loads medical_benchmark_data.json once for all tests in the class.
     * 
     * @TestInstance(PER_CLASS) allows @BeforeAll with instance methods.
     */
    @BeforeAll
    void setupMedicalData() throws IOException {
        // Clear existing data in the database
        logger.info("Clearing graph store...");
        graphStore.clear().block();
        logger.info("Clearing chunk store...");
        chunkStore.deleteAll().block();
        logger.info("Data cleared, starting fresh load");

        // Load medical benchmark dataset
        loadMedicalBenchmarkData();
        logger.info("Medical benchmark data loaded once for all tests in class");
    }

    /**
     * Loads the canonical medical benchmark dataset from
     * medical_benchmark_data.json.
     * This method uses the KnowledgeIndexer to ensure proper dual persistence
     * (database + in-memory graph) and automatic embedding generation.
     */
    protected void loadMedicalBenchmarkData() throws IOException {
        JsonNode root = objectMapper.readTree(
                new ClassPathResource("medical_benchmark_data.json").getInputStream());

        // Load Nodes via KnowledgeIndexer (handles dual persistence + embedding
        // generation)
        int patientCount = 0;
        for (JsonNode nodeJson : root.get("nodes")) {
            UUID id = UUID.fromString(nodeJson.get("id").asText());
            String label = nodeJson.get("label").asText();
            Map<String, Object> props = objectMapper.convertValue(nodeJson.get("properties"), Map.class);
            Node node = new Node(id, label, props, null);
            node.setNew(false); // Mark as existing to preserve ID during save

            if ("Patient".equals(label) && patientCount < 3) {
                logger.info("BEFORE indexNode: id={}, isNew={}", node.getId(), node.isNew());
            }

            Node saved = indexer.indexNode(node).block(); // Framework handles dual persistence

            if ("Patient".equals(label) && patientCount++ < 3) {
                logger.info("AFTER indexNode: original_id={}, saved_id={}, name={}",
                        id, saved.getId(), props.get("name"));
            }
        }

        // Load Edges via KnowledgeIndexer (handles dual persistence)
        for (JsonNode edgeJson : root.get("edges")) {
            UUID id = UUID.fromString(edgeJson.get("id").asText());
            UUID sourceId = UUID.fromString(edgeJson.get("sourceNodeId").asText());
            UUID targetId = UUID.fromString(edgeJson.get("targetNodeId").asText());
            String type = edgeJson.get("relationType").asText();
            Map<String, Object> props = objectMapper.convertValue(edgeJson.get("properties"), Map.class);
            Edge edge = new Edge(id, type, sourceId, targetId, props, 1.0);
            edge.setNew(false); // Mark as existing to preserve ID during save
            indexer.indexEdge(edge).block(); // Framework handles dual persistence
        }

        // Load Chunks via KnowledgeIndexer (handles embedding generation + storage)
        int chunkCount = 0;
        for (JsonNode chunkJson : root.get("chunks")) {
            Chunk chunk = new Chunk();
            UUID originalChunkId = UUID.fromString(chunkJson.get("id").asText());
            chunk.setId(originalChunkId);
            chunk.setContent(chunkJson.get("content").asText());
            chunk.setNew(false); // Mark as existing to preserve ID during save

            UUID linkedNodeId = null;
            if (chunkJson.has("linkedNodeId")) {
                linkedNodeId = UUID.fromString(chunkJson.get("linkedNodeId").asText());
                chunk.setLinkedNodeId(linkedNodeId);
            }
            if (chunkJson.has("metadata")) {
                chunk.setMetadata(objectMapper.convertValue(chunkJson.get("metadata"), Map.class));
            }

            Chunk saved = indexer.indexChunk(chunk).block(); // Framework handles embedding + storage

            if (chunkCount++ < 3) {
                logger.info("Chunk: original_id={}, saved_id={}, linkedNodeId={}",
                        originalChunkId, saved.getId(), linkedNodeId);
            }
        }

        logger.info("Loaded medical benchmark data: {} nodes, {} edges, {} chunks",
                root.get("nodes").size(), root.get("edges").size(), root.get("chunks").size());
    }
}
