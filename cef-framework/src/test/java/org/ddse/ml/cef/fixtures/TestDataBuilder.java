package org.ddse.ml.cef.fixtures;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent builder for creating test data entities.
 * Provides a clean, readable API for constructing Nodes, Edges, and Chunks in
 * tests.
 * 
 * <p>
 * Usage:
 * 
 * <pre>
 * Node patient = TestDataBuilder.node()
 *         .label("Patient")
 *         .property("name", "John Doe")
 *         .property("age", 45)
 *         .vectorizableContent("Patient John Doe, 45 years old")
 *         .build();
 * </pre>
 * 
 * @author mrmanna
 */
public class TestDataBuilder {

    /**
     * Start building a Node.
     */
    public static NodeBuilder node() {
        return new NodeBuilder();
    }

    /**
     * Start building an Edge.
     */
    public static EdgeBuilder edge() {
        return new EdgeBuilder();
    }

    /**
     * Start building a Chunk.
     */
    public static ChunkBuilder chunk() {
        return new ChunkBuilder();
    }

    /**
     * Builder for Node entities.
     */
    public static class NodeBuilder {
        private UUID id = UUID.randomUUID();
        private String label;
        private Map<String, Object> properties = new HashMap<>();
        private String vectorizableContent;
        private Instant created = Instant.now();
        private Instant updated = Instant.now();

        public NodeBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public NodeBuilder label(String label) {
            this.label = label;
            return this;
        }

        public NodeBuilder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public NodeBuilder properties(Map<String, Object> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public NodeBuilder vectorizableContent(String content) {
            this.vectorizableContent = content;
            return this;
        }

        public NodeBuilder created(Instant created) {
            this.created = created;
            return this;
        }

        public NodeBuilder updated(Instant updated) {
            this.updated = updated;
            return this;
        }

        public Node build() {
            if (label == null) {
                throw new IllegalStateException("Node label is required");
            }

            Node node = new Node();
            node.setId(id);
            node.setLabel(label);
            node.setProperties(properties);
            node.setVectorizableContent(vectorizableContent);
            node.setCreated(created);
            node.setUpdated(updated);
            return node;
        }
    }

    /**
     * Builder for Edge entities.
     */
    public static class EdgeBuilder {
        private UUID id = UUID.randomUUID();
        private UUID sourceNodeId;
        private UUID targetNodeId;
        private String relationType;
        private Map<String, Object> properties = new HashMap<>();
        private Double weight = 1.0;
        private Instant created = Instant.now();

        public EdgeBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public EdgeBuilder from(UUID sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
            return this;
        }

        public EdgeBuilder to(UUID targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public EdgeBuilder relationType(String relationType) {
            this.relationType = relationType;
            return this;
        }

        public EdgeBuilder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public EdgeBuilder properties(Map<String, Object> properties) {
            this.properties.putAll(properties);
            return this;
        }

        public EdgeBuilder weight(Double weight) {
            this.weight = weight;
            return this;
        }

        public EdgeBuilder created(Instant created) {
            this.created = created;
            return this;
        }

        public Edge build() {
            if (sourceNodeId == null || targetNodeId == null || relationType == null) {
                throw new IllegalStateException("Edge requires sourceNodeId, targetNodeId, and relationType");
            }

            Edge edge = new Edge();
            edge.setId(id);
            edge.setSourceNodeId(sourceNodeId);
            edge.setTargetNodeId(targetNodeId);
            edge.setRelationType(relationType);
            edge.setProperties(properties);
            edge.setWeight(weight);
            edge.setCreated(created);
            return edge;
        }
    }

    /**
     * Builder for Chunk entities.
     */
    public static class ChunkBuilder {
        private UUID id = UUID.randomUUID();
        private String content;
        private float[] embedding;
        private UUID linkedNodeId;
        private Map<String, Object> metadata = new HashMap<>();
        private Instant created = Instant.now();
        private EmbeddingModel embeddingModel;

        public ChunkBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public ChunkBuilder content(String content) {
            this.content = content;
            return this;
        }

        public ChunkBuilder embedding(float[] embedding) {
            this.embedding = embedding;
            return this;
        }

        /**
         * Generate real embedding using provided EmbeddingModel.
         * This calls the actual Ollama service.
         */
        public ChunkBuilder withRealEmbedding(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public ChunkBuilder linkedTo(UUID nodeId) {
            this.linkedNodeId = nodeId;
            return this;
        }

        public ChunkBuilder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ChunkBuilder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        public ChunkBuilder created(Instant created) {
            this.created = created;
            return this;
        }

        public Chunk build() {
            if (content == null) {
                throw new IllegalStateException("Chunk content is required");
            }

            // Generate real embedding if model provided
            if (embeddingModel != null && embedding == null) {
                embedding = embeddingModel.embed(content);
            }

            Chunk chunk = new Chunk();
            chunk.setId(id);
            chunk.setContent(content);
            chunk.setEmbedding(embedding);
            chunk.setLinkedNodeId(linkedNodeId);
            chunk.setMetadata(metadata);
            chunk.setCreated(created);
            return chunk;
        }
    }
}
