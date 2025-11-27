package org.ddse.ml.cef.service;

import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.Constraint;
import org.ddse.ml.cef.dto.ConstraintType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for evaluating constraints during graph traversal.
 * Used by KnowledgeRetrieverImpl for pattern-based queries.
 */
@Component
public class ConstraintEvaluator {

    /**
     * Evaluate constraints on a node at given step.
     */
    public boolean evaluate(Node node, List<Constraint> constraints, int currentStep) {
        if (constraints == null || constraints.isEmpty()) {
            return true;
        }

        for (Constraint constraint : constraints) {
            // Skip if constraint is for different step
            if (constraint.atStep() != -1 && constraint.atStep() != currentStep) {
                continue;
            }

            // Skip if constraint is for different label
            if (constraint.nodeLabel() != null && !constraint.nodeLabel().equals(node.getLabel())) {
                continue;
            }

            // Evaluate constraint
            if (!evaluateSingle(node, constraint)) {
                return false; // Constraint failed
            }
        }

        return true; // All constraints passed
    }

    private boolean evaluateSingle(Node node, Constraint constraint) {
        Object propertyValue = getProperty(node, constraint.propertyPath());

        return switch (constraint.type()) {
            case PROPERTY_EQUALS -> Objects.equals(propertyValue, constraint.value());
            case PROPERTY_IN -> {
                if (constraint.value() instanceof List<?> list) {
                    yield list.contains(propertyValue);
                }
                yield false;
            }
            case NOT_IN -> {
                if (constraint.value() instanceof List<?> list) {
                    yield !list.contains(propertyValue);
                }
                yield true;
            }
            case GREATER_THAN -> compareNumbers(propertyValue, constraint.value()) > 0;
            case LESS_THAN -> compareNumbers(propertyValue, constraint.value()) < 0;
            case GREATER_THAN_EQUALS -> compareNumbers(propertyValue, constraint.value()) >= 0;
            case LESS_THAN_EQUALS -> compareNumbers(propertyValue, constraint.value()) <= 0;
            case CONTAINS -> {
                if (propertyValue instanceof String s && constraint.value() instanceof String v) {
                    yield s.contains(v);
                } else if (propertyValue instanceof List<?> list) {
                    yield list.contains(constraint.value());
                }
                yield false;
            }
            case STARTS_WITH -> {
                if (propertyValue instanceof String s && constraint.value() instanceof String v) {
                    yield s.startsWith(v);
                }
                yield false;
            }
            case ENDS_WITH -> {
                if (propertyValue instanceof String s && constraint.value() instanceof String v) {
                    yield s.endsWith(v);
                }
                yield false;
            }
            case REGEX_MATCH -> {
                if (propertyValue instanceof String s && constraint.value() instanceof String pattern) {
                    yield s.matches(pattern);
                }
                yield false;
            }
        };
    }

    private Object getProperty(Node node, String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) {
            return null;
        }

        // Simple property from node
        if (!propertyPath.contains(".")) {
            return node.getProperties().get(propertyPath);
        }

        // Navigate property path (e.g., "properties.name" or just "name")
        String[] parts = propertyPath.split("\\.");
        Object current = node.getProperties();

        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    private int compareNumbers(Object a, Object b) {
        if (a == null || b == null) {
            return 0;
        }

        try {
            double da = Double.parseDouble(a.toString());
            double db = Double.parseDouble(b.toString());
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
