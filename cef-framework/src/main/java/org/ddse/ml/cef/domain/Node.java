package org.ddse.ml.cef.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Core domain entity representing a node in the knowledge graph.
 * Framework-agnostic: label and properties are user-defined.
 *
 * @author mrmanna
 */
@Table("nodes")
public class Node implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    /**
     * User-defined label (e.g., "Patient", "Product", "Document")
     * Framework doesn't validate or constrain this
     */
    @Column("label")
    private String label;

    /**
     * Flexible property bag - framework agnostic
     * User stores whatever they need
     */
    @Column("properties")
    private Map<String, Object> properties;

    /**
     * Optional: Text content for automatic vectorization
     * If provided, framework auto-generates embedding
     */
    @Column("vectorizable_content")
    private String vectorizableContent;

    @Column("created")
    private Instant created;

    @Column("updated")
    private Instant updated;

    @Version
    private Long version;

    // Constructors
    public Node() {
        this.id = UUID.randomUUID();
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    public Node(UUID id, String label, Map<String, Object> properties, String vectorizableContent) {
        this.id = id != null ? id : UUID.randomUUID();
        this.label = label;
        this.properties = properties;
        this.vectorizableContent = vectorizableContent;
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public String getVectorizableContent() {
        return vectorizableContent;
    }

    public void setVectorizableContent(String vectorizableContent) {
        this.vectorizableContent = vectorizableContent;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
        return "Node{" +
                "id=" + id +
                ", label='" + label + '\'' +
                ", properties=" + properties +
                '}';
    }
}
