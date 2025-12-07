package org.ddse.ml.cef.dto;

import jakarta.validation.constraints.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Validated DTO for node creation/update with comprehensive bean validation.
 * 
 * <p>Provides JSR-380 validation for all node inputs to ensure data integrity
 * before graph operations. Critical for production deployments.
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>id: Optional (generated if null)</li>
 *   <li>label: Required, 1-100 characters, alphanumeric with underscores</li>
 *   <li>vectorizableContent: Max 100,000 characters (for large documents)</li>
 *   <li>properties: Max 100 entries, keys max 100 chars</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
public record ValidatedNodeInput(
        
        UUID id,
        
        @NotBlank(message = "Label is required")
        @Size(min = 1, max = 100, message = "Label must be 1-100 characters")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", 
                 message = "Label must start with letter and contain only letters, numbers, underscores")
        String label,
        
        @Size(max = 100000, message = "Vectorizable content cannot exceed 100,000 characters")
        String vectorizableContent,
        
        @Size(max = 100, message = "Properties cannot have more than 100 entries")
        Map<@NotBlank @Size(max = 100) String, Object> properties,
        
        @Size(max = 50, message = "Metadata cannot have more than 50 entries")
        Map<@NotBlank @Size(max = 100) String, Object> metadata
) {
    
    // Compact constructor with defensive copies
    public ValidatedNodeInput {
        id = id != null ? id : UUID.randomUUID();
        properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Converts to standard NodeInput for backward compatibility.
     */
    public NodeInput toNodeInput() {
        return new NodeInput(id, label, properties, metadata);
    }
    
    /**
     * Creates from standard NodeInput.
     */
    public static ValidatedNodeInput from(NodeInput input) {
        return new ValidatedNodeInput(
                input.id(),
                input.label(),
                null, // NodeInput doesn't have vectorizableContent
                input.properties(),
                input.metadata()
        );
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID id;
        private String label;
        private String vectorizableContent;
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
        
        public Builder vectorizableContent(String content) {
            this.vectorizableContent = content;
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
        
        public ValidatedNodeInput build() {
            return new ValidatedNodeInput(id, label, vectorizableContent, properties, metadata);
        }
    }
}
