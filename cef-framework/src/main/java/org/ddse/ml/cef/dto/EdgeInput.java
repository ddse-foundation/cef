package org.ddse.ml.cef.dto;

import org.ddse.ml.cef.domain.RelationSemantics;
import org.ddse.ml.cef.domain.RelationType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Input DTO for edge indexing operations.
 * Represents an edge to be added to the knowledge graph.
 */
public record EdgeInput(
        UUID id,
        UUID sourceId,
        UUID targetId,
        String relationType,
        RelationSemantics semantics,
        Double weight,
        Map<String, Object> properties,
        Map<String, Object> metadata) {
    public EdgeInput {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId cannot be null");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId cannot be null");
        }
        if (relationType == null || relationType.isBlank()) {
            throw new IllegalArgumentException("relationType cannot be null or empty");
        }
        properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private UUID sourceId;
        private UUID targetId;
        private String relationType;
        private RelationSemantics semantics = RelationSemantics.ASSOCIATIVE;
        private Double weight = 1.0;
        private Map<String, Object> properties = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder sourceId(UUID sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder targetId(UUID targetId) {
            this.targetId = targetId;
            return this;
        }

        public Builder relationType(String relationType) {
            this.relationType = relationType;
            return this;
        }

        public Builder semantics(RelationSemantics semantics) {
            this.semantics = semantics;
            return this;
        }

        public Builder weight(Double weight) {
            this.weight = weight;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = new HashMap<>(properties);
            return this;
        }

        public Builder property(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder metadataEntry(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public EdgeInput build() {
            return new EdgeInput(id, sourceId, targetId, relationType, semantics,
                    weight, properties, metadata);
        }
    }
}
