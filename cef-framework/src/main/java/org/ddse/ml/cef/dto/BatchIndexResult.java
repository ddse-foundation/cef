package org.ddse.ml.cef.dto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Result DTO for batch indexing operations.
 * Represents the outcome of indexing multiple items.
 */
public record BatchIndexResult(
        int totalItems,
        int successCount,
        int failedCount,
        int skippedCount,
        List<IndexResult> results,
        Duration totalDuration) {
    public BatchIndexResult {
        results = results != null ? List.copyOf(results) : List.of();
        if (totalItems != successCount + failedCount + skippedCount) {
            throw new IllegalArgumentException(
                    "totalItems must equal successCount + failedCount + skippedCount");
        }
    }

    public boolean allSuccess() {
        return failedCount == 0 && skippedCount == 0;
    }

    public boolean hasFailures() {
        return failedCount > 0;
    }

    public double successRate() {
        return totalItems > 0 ? (double) successCount / totalItems : 0.0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<IndexResult> results = new ArrayList<>();
        private int successCount = 0;
        private int failedCount = 0;
        private int skippedCount = 0;
        private Duration totalDuration;

        public Builder addResult(IndexResult result) {
            results.add(result);
            switch (result.status()) {
                case SUCCESS -> successCount++;
                case FAILED -> failedCount++;
                case SKIPPED -> skippedCount++;
            }
            return this;
        }

        public Builder results(List<IndexResult> results) {
            results.forEach(this::addResult);
            return this;
        }

        public Builder totalDuration(Duration duration) {
            this.totalDuration = duration;
            return this;
        }

        public BatchIndexResult build() {
            int totalItems = successCount + failedCount + skippedCount;
            return new BatchIndexResult(
                    totalItems, successCount, failedCount, skippedCount,
                    results, totalDuration);
        }
    }
}
