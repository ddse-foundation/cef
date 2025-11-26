package org.ddse.ml.cef.util;

import org.ddse.ml.cef.domain.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test data builder for creating test entities with sensible defaults.
 */
public class TestDataBuilder {

    public static Node.Builder node() {
        return new Node.Builder();
    }

    public static Edge.Builder edge() {
        return new Edge.Builder();
    }

    public static Chunk.Builder chunk() {
        return new Chunk.Builder();
    }

    public static RelationType.Builder relationType() {
        return new RelationType.Builder();
    }

    public static class Node {
        public static class Builder {
            private UUID id = UUID.randomUUID();
            private String label = "TestNode";
            private Map<String, Object> properties = new HashMap<>();
            private String vectorizableContent;
            private Instant created = Instant.now();
            private Instant updated = Instant.now();

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder label(String label) {
                this.label = label;
                return this;
            }

            public Builder property(String key, Object value) {
                this.properties.put(key, value);
                return this;
            }

            public Builder properties(Map<String, Object> properties) {
                this.properties = new HashMap<>(properties);
                return this;
            }

            public Builder vectorizableContent(String content) {
                this.vectorizableContent = content;
                return this;
            }

            public org.ddse.ml.cef.domain.Node build() {
                org.ddse.ml.cef.domain.Node node = new org.ddse.ml.cef.domain.Node();
                node.setId(id);
                node.setLabel(label);
                node.setProperties(properties);
                node.setVectorizableContent(vectorizableContent);
                node.setCreated(created);
                node.setUpdated(updated);
                return node;
            }
        }
    }

    public static class Edge {
        public static class Builder {
            private UUID id = UUID.randomUUID();
            private String relationType = "TEST_RELATION";
            private UUID sourceNodeId = UUID.randomUUID();
            private UUID targetNodeId = UUID.randomUUID();
            private Map<String, Object> properties = new HashMap<>();
            private Double weight;
            private Instant created = Instant.now();

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder relationType(String relationType) {
                this.relationType = relationType;
                return this;
            }

            public Builder from(UUID sourceNodeId) {
                this.sourceNodeId = sourceNodeId;
                return this;
            }

            public Builder to(UUID targetNodeId) {
                this.targetNodeId = targetNodeId;
                return this;
            }

            public Builder property(String key, Object value) {
                this.properties.put(key, value);
                return this;
            }

            public Builder weight(Double weight) {
                this.weight = weight;
                return this;
            }

            public org.ddse.ml.cef.domain.Edge build() {
                org.ddse.ml.cef.domain.Edge edge = new org.ddse.ml.cef.domain.Edge();
                edge.setId(id);
                edge.setRelationType(relationType);
                edge.setSourceNodeId(sourceNodeId);
                edge.setTargetNodeId(targetNodeId);
                edge.setProperties(properties);
                edge.setWeight(weight);
                edge.setCreated(created);
                return edge;
            }
        }
    }

    public static class Chunk {
        public static class Builder {
            private UUID id = UUID.randomUUID();
            private String content = "Test chunk content";
            private float[] embedding;
            private UUID linkedNodeId;
            private Map<String, Object> metadata = new HashMap<>();
            private Instant created = Instant.now();

            public Builder id(UUID id) {
                this.id = id;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder embedding(float[] embedding) {
                this.embedding = embedding;
                return this;
            }

            public Builder linkedTo(UUID nodeId) {
                this.linkedNodeId = nodeId;
                return this;
            }

            public Builder metadata(String key, Object value) {
                this.metadata.put(key, value);
                return this;
            }

            public org.ddse.ml.cef.domain.Chunk build() {
                org.ddse.ml.cef.domain.Chunk chunk = new org.ddse.ml.cef.domain.Chunk();
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

    public static class RelationType {
        public static class Builder {
            private String name = "TEST_RELATION";
            private RelationSemantics semantics = RelationSemantics.ASSOCIATIVE;
            private boolean bidirectional = false;
            private String description = "Test relation type";
            private Map<String, Object> metadata = new HashMap<>();

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder semantics(RelationSemantics semantics) {
                this.semantics = semantics;
                return this;
            }

            public Builder bidirectional(boolean bidirectional) {
                this.bidirectional = bidirectional;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public org.ddse.ml.cef.domain.RelationType build() {
                // RelationType is immutable - use constructor
                // Note: bidirectional parameter is inverted to directed
                String sourceLabel = metadata.getOrDefault("sourceLabel", "Source").toString();
                String targetLabel = metadata.getOrDefault("targetLabel", "Target").toString();
                return new org.ddse.ml.cef.domain.RelationType(
                        name,
                        sourceLabel,
                        targetLabel,
                        semantics,
                        !bidirectional); // bidirectional -> directed (inverted)
            }
        }
    }
}
