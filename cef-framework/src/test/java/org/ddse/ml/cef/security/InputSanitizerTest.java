package org.ddse.ml.cef.security;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InputSanitizer - validates input sanitization and injection prevention.
 *
 * @author mrmanna
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Input Sanitization Tests")
class InputSanitizerTest {

    private InputSanitizer sanitizer;
    private CefSecurityProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CefSecurityProperties();
        properties.getSanitization().setEnabled(true);
        sanitizer = new InputSanitizer(properties);
    }

    // ==================== Basic Sanitization Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should pass through clean input unchanged")
    void shouldPassThroughCleanInput() {
        String input = "Hello, this is a normal query about medical conditions.";
        String result = sanitizer.sanitize(input);
        assertThat(result).isEqualTo(input);
    }

    @Test
    @Order(2)
    @DisplayName("Should handle null input")
    void shouldHandleNullInput() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitizeLabel(null)).isNull();
        assertThat(sanitizer.sanitizeRelationType(null)).isNull();
    }

    @Test
    @Order(3)
    @DisplayName("Should trim whitespace")
    void shouldTrimWhitespace() {
        String input = "  hello world  ";
        String result = sanitizer.sanitize(input);
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    @Order(4)
    @DisplayName("Should reject input exceeding maximum length")
    void shouldRejectInputExceedingMaxLength() {
        properties.getSanitization().setMaxInputLength(100);
        String longInput = "a".repeat(200);
        
        assertThatThrownBy(() -> sanitizer.sanitize(longInput))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("exceeds maximum");
    }

    // ==================== SQL Injection Detection ====================

    @Test
    @Order(5)
    @DisplayName("Should detect basic SQL injection - SELECT")
    void shouldDetectBasicSqlInjectionSelect() {
        String malicious = "'; SELECT * FROM users; --";
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Should detect SQL injection - DROP TABLE")
    void shouldDetectSqlInjectionDropTable() {
        String malicious = "test'; DROP TABLE users; --";
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("Should detect SQL injection - UNION SELECT")
    void shouldDetectSqlInjectionUnion() {
        String malicious = "1 UNION SELECT username, password FROM users";
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("Should detect SQL injection - OR 1=1")
    void shouldDetectSqlInjectionOr() {
        String malicious = "' OR '1'='1";
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("Should detect SQL injection - comment injection")
    void shouldDetectSqlInjectionComment() {
        String malicious = "admin'--";
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @ParameterizedTest
    @Order(10)
    @ValueSource(strings = {
            "'; DELETE FROM nodes WHERE '1'='1",
            "1; INSERT INTO users VALUES ('hacker', 'password')",
            "UPDATE users SET role='admin' WHERE id=1",
            "test' AND 1=1--",
            "TRUNCATE TABLE users;"
    })
    @DisplayName("Should detect various SQL injection patterns")
    void shouldDetectVariousSqlInjections(String malicious) {
        assertThat(sanitizer.containsSqlInjection(malicious)).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("Should throw exception on SQL injection when enabled")
    void shouldThrowOnSqlInjection() {
        String malicious = "'; SELECT * FROM users; --";
        
        assertThatThrownBy(() -> sanitizer.sanitize(malicious))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("SQL injection");
    }

    @Test
    @Order(12)
    @DisplayName("Should allow normal SQL-like words in context")
    void shouldAllowNormalSqlLikeWords() {
        // These are legitimate uses of words that appear in SQL
        String legitimate1 = "Please select the best option from the list";
        String legitimate2 = "I want to delete my account";
        String legitimate3 = "The union of two sets";
        
        // These should NOT be detected as SQL injection
        assertThat(sanitizer.containsSqlInjection(legitimate1)).isFalse();
        assertThat(sanitizer.containsSqlInjection(legitimate2)).isFalse();
        assertThat(sanitizer.containsSqlInjection(legitimate3)).isFalse();
    }

    // ==================== Cypher Injection Detection ====================

    @Test
    @Order(13)
    @DisplayName("Should detect Cypher injection - MATCH DELETE")
    void shouldDetectCypherInjectionMatchDelete() {
        String malicious = "test'}) MATCH (n) DELETE n //";
        assertThat(sanitizer.containsCypherInjection(malicious)).isTrue();
    }

    @Test
    @Order(14)
    @DisplayName("Should throw exception on Cypher injection")
    void shouldThrowOnCypherInjection() {
        String malicious = "test'}) MATCH (n) DETACH DELETE n //";
        
        assertThatThrownBy(() -> sanitizer.sanitizeForCypher(malicious))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cypher injection");
    }

    // ==================== HTML Stripping ====================

    @Test
    @Order(15)
    @DisplayName("Should strip HTML tags")
    void shouldStripHtmlTags() {
        String input = "<script>alert('xss')</script>Hello <b>World</b>";
        String result = sanitizer.stripHtml(input);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("<b>");
        assertThat(result).contains("Hello");
        assertThat(result).contains("World");
    }

    @Test
    @Order(16)
    @DisplayName("Should strip HTML entities")
    void shouldStripHtmlEntities() {
        String input = "Test &lt;script&gt; &amp; more";
        String result = sanitizer.stripHtml(input);
        assertThat(result).doesNotContain("&lt;");
        assertThat(result).doesNotContain("&gt;");
        assertThat(result).doesNotContain("&amp;");
    }

    @Test
    @Order(17)
    @DisplayName("Should strip javascript: URLs")
    void shouldStripJavascriptUrls() {
        String input = "Click javascript:alert('xss') here";
        String result = sanitizer.stripHtml(input);
        assertThat(result).doesNotContain("javascript:");
    }

    // ==================== Label Sanitization ====================

    @Test
    @Order(18)
    @DisplayName("Should sanitize label to alphanumeric and underscore")
    void shouldSanitizeLabelToAlphanumeric() {
        String input = "Patient-Record (2023)";
        String result = sanitizer.sanitizeLabel(input);
        assertThat(result).isEqualTo("Patient_Record_2023");
    }

    @Test
    @Order(19)
    @DisplayName("Should remove consecutive underscores from label")
    void shouldRemoveConsecutiveUnderscores() {
        String input = "Test__Multiple___Underscores";
        String result = sanitizer.sanitizeLabel(input);
        assertThat(result).isEqualTo("Test_Multiple_Underscores");
    }

    @Test
    @Order(20)
    @DisplayName("Should reject label exceeding maximum length")
    void shouldRejectLongLabel() {
        properties.getSanitization().setMaxLabelLength(50);
        String longLabel = "a".repeat(100);
        
        assertThatThrownBy(() -> sanitizer.sanitizeLabel(longLabel))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    @Order(21)
    @DisplayName("Should reject label with no valid characters")
    void shouldRejectEmptyLabel() {
        String input = "!!!@@@###";
        
        assertThatThrownBy(() -> sanitizer.sanitizeLabel(input))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("no valid characters");
    }

    // ==================== Relation Type Sanitization ====================

    @Test
    @Order(22)
    @DisplayName("Should convert relation type to UPPER_SNAKE_CASE")
    void shouldConvertRelationTypeToUpperSnakeCase() {
        String input = "has-condition";
        String result = sanitizer.sanitizeRelationType(input);
        assertThat(result).isEqualTo("HAS_CONDITION");
    }

    @Test
    @Order(23)
    @DisplayName("Should reject long relation type")
    void shouldRejectLongRelationType() {
        properties.getSanitization().setMaxRelationTypeLength(20);
        String longType = "A".repeat(50);
        
        assertThatThrownBy(() -> sanitizer.sanitizeRelationType(longType))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("exceeds maximum");
    }

    // ==================== SQL Safe Sanitization ====================

    @Test
    @Order(24)
    @DisplayName("Should escape single quotes for SQL")
    void shouldEscapeSingleQuotesForSql() {
        String input = "O'Brien's data";
        String result = sanitizer.sanitizeForSql(input);
        assertThat(result).isEqualTo("O''Brien''s data");
    }

    @Test
    @Order(25)
    @DisplayName("Should remove null bytes")
    void shouldRemoveNullBytes() {
        String input = "test\0data";
        String result = sanitizer.sanitizeForSql(input);
        assertThat(result).doesNotContain("\0");
    }

    // ==================== UUID Validation ====================

    @Test
    @Order(26)
    @DisplayName("Should validate correct UUID format")
    void shouldValidateCorrectUuid() {
        assertThat(sanitizer.isValidUuid("123e4567-e89b-12d3-a456-426614174000")).isTrue();
        assertThat(sanitizer.isValidUuid("550e8400-e29b-41d4-a716-446655440000")).isTrue();
    }

    @Test
    @Order(27)
    @DisplayName("Should reject invalid UUID formats")
    void shouldRejectInvalidUuids() {
        assertThat(sanitizer.isValidUuid(null)).isFalse();
        assertThat(sanitizer.isValidUuid("")).isFalse();
        assertThat(sanitizer.isValidUuid("not-a-uuid")).isFalse();
        assertThat(sanitizer.isValidUuid("123e4567-e89b-12d3-a456")).isFalse();
        assertThat(sanitizer.isValidUuid("123e4567e89b12d3a456426614174000")).isFalse();
    }

    // ==================== Properties Sanitization ====================

    @Test
    @Order(28)
    @DisplayName("Should sanitize properties map")
    void shouldSanitizePropertiesMap() {
        Map<String, Object> props = new HashMap<>();
        props.put("name-key", "value with <script>alert('xss')</script>");
        props.put("count", 42);
        
        Map<String, Object> result = sanitizer.sanitizeProperties(props);
        
        assertThat(result).containsKey("name_key");
        String stringValue = (String) result.get("name_key");
        assertThat(stringValue).doesNotContain("<script>");
        assertThat(result.get("count")).isEqualTo(42);
    }

    // ==================== Disabled Sanitization ====================

    @Test
    @Order(29)
    @DisplayName("Should pass through when sanitization is disabled")
    void shouldPassThroughWhenDisabled() {
        properties.getSanitization().setEnabled(false);
        
        String malicious = "'; SELECT * FROM users; --";
        String result = sanitizer.sanitize(malicious);
        
        assertThat(result).isEqualTo(malicious);
    }
}
