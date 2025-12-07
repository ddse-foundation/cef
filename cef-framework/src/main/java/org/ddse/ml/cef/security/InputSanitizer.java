package org.ddse.ml.cef.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input sanitization service to prevent injection attacks.
 * 
 * <p>Provides methods to sanitize user inputs before they are used in:
 * <ul>
 *   <li>Database queries (SQL injection prevention)</li>
 *   <li>Graph queries (Cypher injection prevention)</li>
 *   <li>Template rendering (XSS prevention)</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
@Component
public class InputSanitizer {

    private static final Logger log = LoggerFactory.getLogger(InputSanitizer.class);

    private final CefSecurityProperties securityProperties;

    // Common SQL injection patterns - require SQL-specific syntax markers
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            // SQL commands preceded by quote (clear injection)
            "('\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE)\\b)|" +
            // SQL SELECT with asterisk
            "(\\bSELECT\\s+\\*)|" +
            // SQL SELECT with function call like COUNT(), MAX()
            "(\\bSELECT\\s+[a-z_]+\\s*\\()|" +
            // DELETE FROM table (note: requires identifier-like name after FROM)
            "(\\bDELETE\\s+FROM\\s+[a-z_][a-z0-9_]*\\s*(WHERE|;|$))|" +
            // INSERT INTO table
            "(\\bINSERT\\s+INTO\\s+[a-z_][a-z0-9_]*\\s*[\\(VALUES])|" +
            // UPDATE table SET
            "(\\bUPDATE\\s+[a-z_][a-z0-9_]*\\s+SET\\s+[a-z_])|" +
            // DROP/TRUNCATE TABLE tablename
            "(\\b(DROP|TRUNCATE)\\s+(TABLE|DATABASE|INDEX)\\s+[a-z_])|" +
            // UNION SELECT
            "(\\bUNION\\s+(ALL\\s+)?SELECT\\b)|" +
            // Chained commands with semicolon
            "(;\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|TRUNCATE))|" +
            // SQL comments after quote or at end of line
            "('.*--|--\\s*$|/\\*|\\*/)|" +
            // Stored procedure execution
            "(\\b(xp_|sp_)[a-z_]+)|" +
            // Tautologies with quotes
            "('\\s*(OR|AND)\\s*')|" +
            "(\\b(OR|AND)\\b\\s+\\d+\\s*=\\s*\\d+)|" +
            "('\\s*=\\s*')|" +
            // Hex-encoded values (often injection)
            "(0x[0-9a-fA-F]{4,})",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // Cypher injection patterns (for Neo4j and AGE)
    private static final Pattern CYPHER_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(MATCH|CREATE|MERGE|DELETE|DETACH|SET|REMOVE|RETURN|WITH|UNWIND|CALL|LOAD|USING)\\b.*\\b(MATCH|CREATE|MERGE|DELETE|SET|REMOVE|WHERE)\\b)|" +
            "(//|/\\*|\\*/)|" +
            "(\\}\\s*\\)\\s*(MATCH|CREATE|DELETE))",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    // HTML/Script injection patterns
    private static final Pattern HTML_PATTERN = Pattern.compile(
            "<[^>]+>|&[a-zA-Z]+;|&#\\d+;",
            Pattern.CASE_INSENSITIVE
    );

    // Script injection patterns
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "(?i)(javascript:|data:|vbscript:|on\\w+\\s*=)",
            Pattern.CASE_INSENSITIVE
    );

    public InputSanitizer(CefSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * Sanitize general text input.
     * 
     * @param input Raw user input
     * @return Sanitized input
     * @throws SecurityException if malicious content is detected
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return input;
        }

        // Check length
        if (input.length() > config.getMaxInputLength()) {
            log.warn("Input exceeds maximum length: {} > {}", input.length(), config.getMaxInputLength());
            throw new SecurityException("Input exceeds maximum allowed length");
        }

        String sanitized = input;

        // Strip HTML if enabled
        if (config.isStripHtml()) {
            sanitized = stripHtml(sanitized);
        }

        // Detect SQL injection
        if (config.isDetectSqlInjection() && containsSqlInjection(sanitized)) {
            log.warn("Potential SQL injection detected in input");
            throw new SecurityException("Potential SQL injection detected");
        }

        return sanitized.trim();
    }

    /**
     * Sanitize input for use in node labels.
     * Labels have stricter requirements - alphanumeric and underscores only.
     * 
     * @param label Raw label input
     * @return Sanitized label
     */
    public String sanitizeLabel(String label) {
        if (label == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return label;
        }

        if (label.length() > config.getMaxLabelLength()) {
            log.warn("Label exceeds maximum length: {} > {}", label.length(), config.getMaxLabelLength());
            throw new SecurityException("Label exceeds maximum allowed length");
        }

        // Labels should be alphanumeric with underscores
        String sanitized = label.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // Remove consecutive underscores
        sanitized = sanitized.replaceAll("_+", "_");
        
        // Remove leading/trailing underscores
        sanitized = sanitized.replaceAll("^_+|_+$", "");

        if (sanitized.isEmpty()) {
            throw new SecurityException("Label contains no valid characters");
        }

        return sanitized;
    }

    /**
     * Sanitize input for use in relation type names.
     * 
     * @param relationType Raw relation type input
     * @return Sanitized relation type
     */
    public String sanitizeRelationType(String relationType) {
        if (relationType == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return relationType;
        }

        if (relationType.length() > config.getMaxRelationTypeLength()) {
            log.warn("Relation type exceeds maximum length: {} > {}", 
                    relationType.length(), config.getMaxRelationTypeLength());
            throw new SecurityException("Relation type exceeds maximum allowed length");
        }

        // Relation types are typically UPPER_SNAKE_CASE
        String sanitized = relationType.toUpperCase()
                .replaceAll("[^A-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        if (sanitized.isEmpty()) {
            throw new SecurityException("Relation type contains no valid characters");
        }

        return sanitized;
    }

    /**
     * Sanitize input for use in SQL queries (prepared statement parameters are preferred).
     * This is a defense-in-depth measure.
     * 
     * @param input Raw input
     * @return SQL-safe string
     */
    public String sanitizeForSql(String input) {
        if (input == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return input;
        }

        // Escape single quotes by doubling them
        String sanitized = input.replace("'", "''");
        
        // Remove null bytes
        sanitized = sanitized.replace("\0", "");
        
        // Remove backslash followed by special chars
        sanitized = sanitized.replaceAll("\\\\[nrtbfav0]", " ");

        return sanitized;
    }

    /**
     * Sanitize input for use in Cypher queries.
     * 
     * @param input Raw input
     * @return Cypher-safe string
     */
    public String sanitizeForCypher(String input) {
        if (input == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return input;
        }

        // Check for Cypher injection patterns
        if (containsCypherInjection(input)) {
            log.warn("Potential Cypher injection detected in input");
            throw new SecurityException("Potential Cypher injection detected");
        }

        // Escape single quotes and backslashes for Cypher
        String sanitized = input
                .replace("\\", "\\\\")
                .replace("'", "\\'");

        return sanitized;
    }

    /**
     * Check if input contains potential SQL injection.
     */
    public boolean containsSqlInjection(String input) {
        if (input == null) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Check if input contains potential Cypher injection.
     */
    public boolean containsCypherInjection(String input) {
        if (input == null) {
            return false;
        }
        return CYPHER_INJECTION_PATTERN.matcher(input).find();
    }

    /**
     * Strip HTML tags from input.
     */
    public String stripHtml(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove HTML tags
        String result = HTML_PATTERN.matcher(input).replaceAll("");
        
        // Remove script-related content
        result = SCRIPT_PATTERN.matcher(result).replaceAll("");
        
        return result;
    }

    /**
     * Validate that input is a valid UUID format.
     */
    public boolean isValidUuid(String input) {
        if (input == null) {
            return false;
        }
        return input.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    /**
     * Sanitize properties map - validate keys and sanitize values.
     */
    public java.util.Map<String, Object> sanitizeProperties(java.util.Map<String, Object> properties) {
        if (properties == null) {
            return null;
        }

        var config = securityProperties.getSanitization();
        if (!config.isEnabled()) {
            return properties;
        }

        java.util.Map<String, Object> sanitized = new java.util.HashMap<>();
        
        for (var entry : properties.entrySet()) {
            String key = sanitizeLabel(entry.getKey());
            Object value = entry.getValue();
            
            if (value instanceof String) {
                sanitized.put(key, sanitize((String) value));
            } else {
                sanitized.put(key, value);
            }
        }
        
        return sanitized;
    }
}
