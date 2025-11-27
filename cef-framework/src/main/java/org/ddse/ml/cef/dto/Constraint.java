package org.ddse.ml.cef.dto;

import java.util.List;

/**
 * Constraint to apply during graph traversal.
 */
public record Constraint(
        ConstraintType type,
        String nodeLabel,
        String propertyPath,
        Object value,
        int atStep) {
    public static Constraint propertyEquals(String label, String property, Object value, int step) {
        return new Constraint(ConstraintType.PROPERTY_EQUALS, label, property, value, step);
    }

    public static Constraint propertyIn(String label, String property, List<?> values, int step) {
        return new Constraint(ConstraintType.PROPERTY_IN, label, property, values, step);
    }

    public static Constraint notIn(String label, String property, List<?> excludeValues, int step) {
        return new Constraint(ConstraintType.NOT_IN, label, property, excludeValues, step);
    }

    public static Constraint greaterThan(String label, String property, Number value, int step) {
        return new Constraint(ConstraintType.GREATER_THAN, label, property, value, step);
    }

    public static Constraint lessThan(String label, String property, Number value, int step) {
        return new Constraint(ConstraintType.LESS_THAN, label, property, value, step);
    }
}
