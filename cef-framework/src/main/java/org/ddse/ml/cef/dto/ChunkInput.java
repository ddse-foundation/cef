package org.ddse.ml.cef.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Input DTO for chunk indexing operations.
 * Represents a text chunk to be added to the vector store.
 */
public record ChunkInput(
        UUID id,
        String content,
        UUID linkedNodeId,
        Map<String, Object> metadata) {
    public ChunkInput {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID id;
        private String content;
        private UUID linkedNodeId;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder linkedNodeId(UUID linkedNodeId) {
            this.linkedNodeId = linkedNodeId;
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

        public ChunkInput build() {
            return new ChunkInput(id, content, linkedNodeId, metadata);
        }
    }
}
