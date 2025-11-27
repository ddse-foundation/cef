package org.ddse.ml.cef.dto;

/**
 * Constraint types supported during traversal.
 */
public enum ConstraintType {
    PROPERTY_EQUALS, // property == value
    PROPERTY_IN, // property IN (value1, value2, ...)
    NOT_IN, // property NOT IN (value1, value2, ...)
    GREATER_THAN, // property > value
    LESS_THAN, // property < value
    GREATER_THAN_EQUALS, // property >= value
    LESS_THAN_EQUALS, // property <= value
    CONTAINS, // property CONTAINS value (string/list)
    STARTS_WITH, // property STARTS_WITH value (string)
    ENDS_WITH, // property ENDS_WITH value (string)
    REGEX_MATCH // property MATCHES regex
}
