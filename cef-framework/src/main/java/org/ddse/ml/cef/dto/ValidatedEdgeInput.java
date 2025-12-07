package org.ddse.ml.cef.dto;

import jakarta.validation.constraints.*;
import org.ddse.ml.cef.domain.RelationSemantics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Validated DTO for edge creation/update with comprehensive bean validation.
 * 
 * <p>Provides JSR-380 validation for all edge inputs to ensure data integrity
 * before graph operations. Critical for maintaining graph consistency.
 * 
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>id: Optional (generated if null)</li>
 *   <li>relationType: Required, 1-50 characters, uppercase with underscores</li>
 *   <li>sourceId: Required (must exist in graph)</li>
 *   <li>targetId: Required (must exist in graph)</li>
 *   <li>weight: 0.0-1.0 normalized weight</li>
 *   <li>properties: Max 50 entries</li>
 * </ul>
 * 
 * @author mrmanna
 * @since v0.6
 */
public record ValidatedEdgeInput(
        
        UUID id,
        
        @NotNull(message = "Source node ID is required")
        UUID sourceId,
        
        @NotNull(message = "Target node ID is required")
        UUID targetId,
        
        @NotBlank(message = "Relation type is required")
        @Size(min = 1, max = 50, message = "Relation type must be 1-50 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", 
                 message = "Relation type must be uppercase with letters, numbers, underscores")
        String relationType,
        
        RelationSemantics semantics,
        
        @DecimalMin(value = "0.0", message = "Weight cannot be negative")
        @DecimalMax(value = "1.0", message = "Weight cannot exceed 1.0")
        Double weight,
        
        @Size(max = 50, message = "Properties cannot have more than 50 entries")
        Map<@NotBlank @Size(max = 100) String, Object> properties,
        
        @Size(max = 50, message = "Metadata cannot have more than 50 entries")
        Map<@NotBlank @Size(max = 100) String, Object> metadata
) {
    
    // Compact constructor with defensive copies and defaults
    public ValidatedEdgeInput {
        id = id != null ? id : UUID.randomUUID();
        semantics = semantics != null ? semantics : RelationSemantics.ASSOCIATIVE;
        weight = weight != null ? weight : 1.0;
        properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
        metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Converts to standard EdgeInput for backward compatibility.
     */
    public EdgeInput toEdgeInput() {
        return new EdgeInput(id, sourceId, targetId, relationType, semantics, weight, properties, metadata);
    }
    
    /**
     * Creates from standard EdgeInput.
     */
    public static ValidatedEdgeInput from(EdgeInput input) {
        return new ValidatedEdgeInput(
                input.id(),
                input.sourceId(),
                input.targetId(),
                input.relationType(),
                input.semantics(),
                input.weight(),
                input.properties(),
                input.metadata()
        );
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
        
        public ValidatedEdgeInput build() {
            return new ValidatedEdgeInput(id, sourceId, targetId, relationType, semantics, weight, properties, metadata);
        }
    }
}
