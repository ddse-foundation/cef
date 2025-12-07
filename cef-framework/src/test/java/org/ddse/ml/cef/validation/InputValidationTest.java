package org.ddse.ml.cef.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.ddse.ml.cef.dto.ValidatedEdgeInput;
import org.ddse.ml.cef.dto.ValidatedNodeInput;
import org.ddse.ml.cef.dto.ValidatedRetrievalRequest;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for CEF input validation using JSR-380 Bean Validation.
 * 
 * @author mrmanna
 * @since v0.6
 */
@DisplayName("CEF Input Validation Tests")
class InputValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // ==================== ValidatedRetrievalRequest Tests ====================

    @Nested
    @DisplayName("ValidatedRetrievalRequest Validation")
    class RetrievalRequestValidation {

        @Test
        @DisplayName("Should accept valid retrieval request")
        void shouldAcceptValidRequest() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("Find patients with diabetes")
                    .topK(10)
                    .maxTokenBudget(4000)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject blank query")
        void shouldRejectBlankQuery() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("")
                    .topK(10)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("query"));
        }

        @Test
        @DisplayName("Should reject null query")
        void shouldRejectNullQuery() {
            var request = ValidatedRetrievalRequest.builder()
                    .query(null)
                    .topK(10)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should use default topK when 0 is provided")
        void shouldDefaultTopKWhenZero() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .topK(0)
                    .build();

            // topK=0 is converted to default (10) in compact constructor
            assertThat(request.topK()).isEqualTo(10);
            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject topK above maximum")
        void shouldRejectTopKAboveMax() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .topK(2000)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject negative token budget")
        void shouldRejectNegativeTokenBudget() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .maxTokenBudget(-100)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should accept maximum token budget")
        void shouldAcceptMaxTokenBudget() {
            var request = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .maxTokenBudget(200000)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject too many semantic keywords")
        void shouldRejectTooManyKeywords() {
            List<String> keywords = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                keywords.add("keyword" + i);
            }

            var request = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .semanticKeywords(keywords)
                    .build();

            Set<ConstraintViolation<ValidatedRetrievalRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== ValidatedNodeInput Tests ====================

    @Nested
    @DisplayName("ValidatedNodeInput Validation")
    class NodeInputValidation {

        @Test
        @DisplayName("Should accept valid node input")
        void shouldAcceptValidNode() {
            var node = ValidatedNodeInput.builder()
                    .label("Patient")
                    .property("name", "John Doe")
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should generate UUID if not provided")
        void shouldGenerateUuidIfNotProvided() {
            var node = ValidatedNodeInput.builder()
                    .label("Patient")
                    .build();

            assertThat(node.id()).isNotNull();
        }

        @Test
        @DisplayName("Should reject blank label")
        void shouldRejectBlankLabel() {
            var node = ValidatedNodeInput.builder()
                    .label("")
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("label"));
        }

        @Test
        @DisplayName("Should reject label with invalid characters")
        void shouldRejectInvalidLabelCharacters() {
            var node = ValidatedNodeInput.builder()
                    .label("Invalid-Label!")
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject label starting with number")
        void shouldRejectLabelStartingWithNumber() {
            var node = ValidatedNodeInput.builder()
                    .label("123Patient")
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should accept label with underscore")
        void shouldAcceptLabelWithUnderscore() {
            var node = ValidatedNodeInput.builder()
                    .label("Medical_Record")
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject too long label")
        void shouldRejectTooLongLabel() {
            var node = ValidatedNodeInput.builder()
                    .label("A".repeat(150))
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject too many properties")
        void shouldRejectTooManyProperties() {
            Map<String, Object> properties = new HashMap<>();
            for (int i = 0; i < 150; i++) {
                properties.put("key" + i, "value" + i);
            }

            var node = ValidatedNodeInput.builder()
                    .label("Patient")
                    .properties(properties)
                    .build();

            Set<ConstraintViolation<ValidatedNodeInput>> violations = validator.validate(node);
            assertThat(violations).isNotEmpty();
        }
    }

    // ==================== ValidatedEdgeInput Tests ====================

    @Nested
    @DisplayName("ValidatedEdgeInput Validation")
    class EdgeInputValidation {

        private final UUID sourceId = UUID.randomUUID();
        private final UUID targetId = UUID.randomUUID();

        @Test
        @DisplayName("Should accept valid edge input")
        void shouldAcceptValidEdge() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should generate UUID if not provided")
        void shouldGenerateUuidIfNotProvided() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            assertThat(edge.id()).isNotNull();
        }

        @Test
        @DisplayName("Should default weight to 1.0")
        void shouldDefaultWeight() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            assertThat(edge.weight()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should reject blank relation type")
        void shouldRejectBlankRelationType() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject lowercase relation type")
        void shouldRejectLowercaseRelationType() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("treats")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should accept relation type with underscore")
        void shouldAcceptRelationTypeWithUnderscore() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("HAS_CONDITION")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should reject null source node ID")
        void shouldRejectNullSourceNodeId() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(null)
                    .targetId(targetId)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject null target node ID")
        void shouldRejectNullTargetNodeId() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(null)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject negative weight")
        void shouldRejectNegativeWeight() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .weight(-0.5)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should reject weight above 1.0")
        void shouldRejectWeightAboveOne() {
            var edge = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .weight(1.5)
                    .build();

            Set<ConstraintViolation<ValidatedEdgeInput>> violations = validator.validate(edge);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Should accept weight at boundary values")
        void shouldAcceptWeightAtBoundaries() {
            var edgeZero = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .weight(0.0)
                    .build();

            var edgeOne = ValidatedEdgeInput.builder()
                    .relationType("TREATS")
                    .sourceId(sourceId)
                    .targetId(targetId)
                    .weight(1.0)
                    .build();

            assertThat(validator.validate(edgeZero)).isEmpty();
            assertThat(validator.validate(edgeOne)).isEmpty();
        }
    }

    // ==================== Conversion Tests ====================

    @Nested
    @DisplayName("DTO Conversion Tests")
    class ConversionTests {

        @Test
        @DisplayName("Should convert ValidatedRetrievalRequest to RetrievalRequest")
        void shouldConvertRetrievalRequest() {
            var validated = ValidatedRetrievalRequest.builder()
                    .query("test query")
                    .topK(20)
                    .maxTokenBudget(8000)
                    .build();

            var standard = validated.toRetrievalRequest();

            assertThat(standard.query()).isEqualTo("test query");
            assertThat(standard.topK()).isEqualTo(20);
            assertThat(standard.maxTokenBudget()).isEqualTo(8000);
        }

        @Test
        @DisplayName("Should convert ValidatedNodeInput to NodeInput")
        void shouldConvertNodeInput() {
            var validated = ValidatedNodeInput.builder()
                    .label("Patient")
                    .property("name", "John")
                    .build();

            var standard = validated.toNodeInput();

            assertThat(standard.label()).isEqualTo("Patient");
            assertThat(standard.properties().get("name")).isEqualTo("John");
        }
    }
}
