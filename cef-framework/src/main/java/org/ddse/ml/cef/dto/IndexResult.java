package org.ddse.ml.cef.dto;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Result DTO for single item indexing operations.
 * Represents the outcome of indexing a node, edge, or chunk.
 */
public record IndexResult(
        UUID itemId,
        ItemType itemType,
        IndexStatus status,
        String message,
        Duration duration,
        Map<String, Object> metadata) {
    public enum ItemType {
        NODE, EDGE, CHUNK
    }

    public enum IndexStatus {
        SUCCESS, FAILED, SKIPPED
    }

    public IndexResult {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId cannot be null");
        }
        if (itemType == null) {
            throw new IllegalArgumentException("itemType cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public boolean isSuccess() {
        return status == IndexStatus.SUCCESS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID itemId;
        private ItemType itemType;
        private IndexStatus status;
        private String message;
        private Duration duration;
        private Map<String, Object> metadata = new HashMap<>();

        public Builder itemId(UUID itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder itemType(ItemType itemType) {
            this.itemType = itemType;
            return this;
        }

        public Builder status(IndexStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
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

        public IndexResult build() {
            return new IndexResult(itemId, itemType, status, message, duration, metadata);
        }
    }
}
