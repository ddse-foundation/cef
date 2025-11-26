package org.ddse.ml.cef.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * User-defined relation type with semantic information.
 * Framework doesn't validate or constrain relation names - user defines them.
 * 
 * Example registrations:
 * - Medical: TREATS (Patient, Disease), PRESCRIBES (Doctor, Medication)
 * - E-commerce: CONTAINS (Order, Product), SHIPS_TO (Order, Address)
 * - Document: REFERENCES (Document, Document), AUTHORED_BY (Document, Person)
 *
 * @author mrmanna
 */
@Table("relation_type")
public class RelationType implements Persistable<String> {

    @Id
    @Column("name")
    private final String name;

    @Column("source_label")
    private final String sourceLabel;

    @Column("target_label")
    private final String targetLabel;

    @Column("semantics")
    private final RelationSemantics semantics;

    @Column("directed")
    private final boolean directed;

    @Transient
    private final boolean isNew = true;

    @PersistenceCreator
    public RelationType(String name, String sourceLabel, String targetLabel,
            RelationSemantics semantics, boolean directed) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Relation type name cannot be null or empty");
        }
        if (sourceLabel == null || sourceLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Source label cannot be null or empty");
        }
        if (targetLabel == null || targetLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Target label cannot be null or empty");
        }
        if (semantics == null) {
            throw new IllegalArgumentException("Semantics cannot be null");
        }

        this.name = name.toUpperCase(); // Normalize to uppercase
        this.sourceLabel = sourceLabel;
        this.targetLabel = targetLabel;
        this.semantics = semantics;
        this.directed = directed;
    }

    @Override
    public String getId() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public RelationSemantics getSemantics() {
        return semantics;
    }

    public boolean isDirected() {
        return directed;
    }

    @Transient
    public boolean isNew() {
        return isNew;
    }

    /**
     * Validates if this relation type can connect the given node labels.
     */
    public boolean canConnect(String srcLabel, String tgtLabel) {
        if (directed) {
            return sourceLabel.equals(srcLabel) && targetLabel.equals(tgtLabel);
        } else {
            return (sourceLabel.equals(srcLabel) && targetLabel.equals(tgtLabel)) ||
                    (sourceLabel.equals(tgtLabel) && targetLabel.equals(srcLabel));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RelationType))
            return false;
        RelationType that = (RelationType) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "RelationType{" +
                "name='" + name + '\'' +
                ", sourceLabel='" + sourceLabel + '\'' +
                ", targetLabel='" + targetLabel + '\'' +
                ", semantics=" + semantics +
                ", directed=" + directed +
                '}';
    }
}
