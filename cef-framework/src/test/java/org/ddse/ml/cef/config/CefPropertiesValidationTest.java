package org.ddse.ml.cef.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CefProperties configuration validation.
 * Ensures all configuration constraints are properly enforced.
 *
 * @author mrmanna
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("CEF Properties Validation Tests")
class CefPropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== Default Values ====================

    @Test
    @Order(1)
    @DisplayName("Should have valid default values")
    void shouldHaveValidDefaults() {
        var props = new CefProperties();
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).isEmpty();
        assertThat(props.getGraph().getStore()).isEqualTo("duckdb");
        assertThat(props.getVector().getStore()).isEqualTo("duckdb");
        assertThat(props.getVector().getDimension()).isEqualTo(768);
        assertThat(props.getMcp().getMaxTokenBudget()).isEqualTo(4000);
        assertThat(props.getMcp().getMaxGraphNodes()).isEqualTo(50);
        assertThat(props.getEmbedding().getBatchSize()).isEqualTo(100);
    }

    // ==================== Vector Dimension Validation ====================

    @Test
    @Order(2)
    @DisplayName("Should reject dimension below minimum")
    void shouldRejectDimensionBelowMinimum() {
        var props = new CefProperties();
        props.getVector().setDimension(50);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("128");
    }

    @Test
    @Order(3)
    @DisplayName("Should reject dimension above maximum")
    void shouldRejectDimensionAboveMaximum() {
        var props = new CefProperties();
        props.getVector().setDimension(5000);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("4096");
    }

    @Test
    @Order(4)
    @DisplayName("Should accept common embedding dimensions")
    void shouldAcceptCommonDimensions() {
        var props = new CefProperties();
        
        // nomic-embed-text
        props.getVector().setDimension(768);
        assertThat(validator.validate(props)).isEmpty();
        
        // OpenAI ada-002
        props.getVector().setDimension(1536);
        assertThat(validator.validate(props)).isEmpty();
        
        // OpenAI text-embedding-3-large
        props.getVector().setDimension(3072);
        assertThat(validator.validate(props)).isEmpty();
    }

    // ==================== MCP Config Validation ====================

    @Test
    @Order(5)
    @DisplayName("Should reject token budget below minimum")
    void shouldRejectTokenBudgetBelowMinimum() {
        var props = new CefProperties();
        props.getMcp().setMaxTokenBudget(50);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .contains("maxTokenBudget");
    }

    @Test
    @Order(6)
    @DisplayName("Should reject token budget above maximum")
    void shouldRejectTokenBudgetAboveMaximum() {
        var props = new CefProperties();
        props.getMcp().setMaxTokenBudget(200000);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(7)
    @DisplayName("Should accept valid token budgets")
    void shouldAcceptValidTokenBudgets() {
        var props = new CefProperties();
        
        // Common context windows
        props.getMcp().setMaxTokenBudget(4000);  // GPT-3.5 safe
        assertThat(validator.validate(props)).isEmpty();
        
        props.getMcp().setMaxTokenBudget(8000);  // GPT-4
        assertThat(validator.validate(props)).isEmpty();
        
        props.getMcp().setMaxTokenBudget(128000);  // GPT-4 Turbo
        assertThat(validator.validate(props)).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Should reject graph nodes below minimum")
    void shouldRejectGraphNodesBelowMinimum() {
        var props = new CefProperties();
        props.getMcp().setMaxGraphNodes(0);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(9)
    @DisplayName("Should reject graph nodes above maximum")
    void shouldRejectGraphNodesAboveMaximum() {
        var props = new CefProperties();
        props.getMcp().setMaxGraphNodes(1000);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    // ==================== Embedding Config Validation ====================

    @Test
    @Order(10)
    @DisplayName("Should reject batch size below minimum")
    void shouldRejectBatchSizeBelowMinimum() {
        var props = new CefProperties();
        props.getEmbedding().setBatchSize(0);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(11)
    @DisplayName("Should reject batch size above maximum")
    void shouldRejectBatchSizeAboveMaximum() {
        var props = new CefProperties();
        props.getEmbedding().setBatchSize(2000);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(12)
    @DisplayName("Should reject cache TTL below minimum")
    void shouldRejectCacheTtlBelowMinimum() {
        var props = new CefProperties();
        props.getEmbedding().setCacheTtlSeconds(30);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(13)
    @DisplayName("Should reject cache TTL above maximum")
    void shouldRejectCacheTtlAboveMaximum() {
        var props = new CefProperties();
        props.getEmbedding().setCacheTtlSeconds(100000);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    // ==================== Store Name Validation ====================

    @Test
    @Order(14)
    @DisplayName("Should reject empty graph store name")
    void shouldRejectEmptyGraphStore() {
        var props = new CefProperties();
        props.getGraph().setStore("");
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    @Test
    @Order(15)
    @DisplayName("Should reject empty vector store name")
    void shouldRejectEmptyVectorStore() {
        var props = new CefProperties();
        props.getVector().setStore("");
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(1);
    }

    // ==================== Multiple Violations ====================

    @Test
    @Order(16)
    @DisplayName("Should detect multiple validation errors")
    void shouldDetectMultipleErrors() {
        var props = new CefProperties();
        props.getVector().setDimension(10);
        props.getMcp().setMaxTokenBudget(50);
        props.getEmbedding().setBatchSize(0);
        
        Set<ConstraintViolation<CefProperties>> violations = validator.validate(props);
        
        assertThat(violations).hasSize(3);
    }

    // ==================== MCP Required Fields ====================

    @Test
    @Order(17)
    @DisplayName("Should have textQuery as default required field")
    void shouldHaveTextQueryAsDefault() {
        var props = new CefProperties();
        
        assertThat(props.getMcp().isFieldRequired("textQuery")).isTrue();
        assertThat(props.getMcp().isFieldRequired("graphHints")).isFalse();
        assertThat(props.getMcp().isFieldRequired("semanticKeywords")).isFalse();
    }

    // ==================== Embedding Cache Config ====================

    @Test
    @Order(18)
    @DisplayName("Should have cache enabled by default")
    void shouldHaveCacheEnabledByDefault() {
        var props = new CefProperties();
        
        assertThat(props.getEmbedding().isCacheEnabled()).isTrue();
        assertThat(props.getEmbedding().getCacheTtlSeconds()).isEqualTo(3600);
    }
}
