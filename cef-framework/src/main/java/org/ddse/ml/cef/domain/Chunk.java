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
 * Document chunk with vector embedding for semantic search.
 *
 * @author mrmanna
 */
@Table("chunks")
public class Chunk implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    /**
     * The actual text content
     */
    @Column("content")
    private String content;

    /**
     * Vector embedding (stored as float array)
     * Dimension configured in application.yml
     */
    @Column("embedding")
    private float[] embedding;

    /**
     * Optional: Link to a graph node for hybrid search
     */
    @Column("linked_node_id")
    private UUID linkedNodeId;

    /**
     * Optional metadata (source, page number, etc.)
     */
    @Column("metadata")
    private Map<String, Object> metadata;

    @Column("created")
    private Instant created;

    // Constructors
    public Chunk() {
        this.id = UUID.randomUUID();
        this.created = Instant.now();
    }

    public Chunk(UUID id, String content, float[] embedding, UUID linkedNodeId, Map<String, Object> metadata) {
        this.id = id != null ? id : UUID.randomUUID();
        this.content = content;
        this.embedding = embedding;
        this.linkedNodeId = linkedNodeId;
        this.metadata = metadata;
        this.created = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public UUID getLinkedNodeId() {
        return linkedNodeId;
    }

    public void setLinkedNodeId(UUID linkedNodeId) {
        this.linkedNodeId = linkedNodeId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
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
        return "Chunk{" +
                "id=" + id +
                ", content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content)
                + '\'' +
                ", linkedNodeId=" + linkedNodeId +
                '}';
    }
}
