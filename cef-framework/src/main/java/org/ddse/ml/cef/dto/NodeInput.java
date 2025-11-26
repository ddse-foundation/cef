package org.ddse.ml.cef.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Input DTO for node indexing operations.
 * Represents a node to be added to the knowledge graph.
 */
public record NodeInput(
        UUID id,
        String label,
        Map<String, Object> properties,
        Map<String, Object> metadata) {
    public NodeInput {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Label cannot be null or empty");
        }
        properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String label;
        private Map<String, Object> properties = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
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

        public NodeInput build() {
            return new NodeInput(id, label, properties, metadata);
        }
    }
}
