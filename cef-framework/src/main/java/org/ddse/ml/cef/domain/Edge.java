package org.ddse.ml.cef.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core domain entity representing an edge (relationship) in the knowledge
 * graph.
 *
 * @author mrmanna
 */
@Table("edges")
public class Edge implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    /**
     * User-defined relation type (e.g., "TREATS", "CONTAINS", "IS_A")
     * Must be registered with framework via initialize()
     */
    @Column("relation_type")
    private String relationType;

    @Column("source_node_id")
    private UUID sourceNodeId;

    @Column("target_node_id")
    private UUID targetNodeId;

    /**
     * Optional properties for the relationship
     */
    @Column("properties")
    private Map<String, Object> properties;

    /**
     * Optional weight for weighted graph algorithms
     */
    @Column("weight")
    private Double weight;

    @Column("created")
    private Instant created;

    // Constructors
    public Edge() {
        this.id = UUID.randomUUID();
        this.created = Instant.now();
    }

    public Edge(UUID id, String relationType, UUID sourceNodeId, UUID targetNodeId,
            Map<String, Object> properties, Double weight) {
        this.id = id != null ? id : UUID.randomUUID();
        this.relationType = relationType;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.properties = properties;
        this.weight = weight;
        this.created = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public UUID getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(UUID sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public UUID getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(UUID targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    @Transient
    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id=" + id +
                ", relationType='" + relationType + '\'' +
                ", sourceNodeId=" + sourceNodeId +
                ", targetNodeId=" + targetNodeId +
                '}';
    }
}
