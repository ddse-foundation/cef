package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.RelationSemantics;
import org.ddse.ml.cef.domain.RelationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
class RelationTypeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort()
                + "/" + postgres.getDatabaseName());
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired
    private RelationTypeRepository relationTypeRepository;

    @BeforeEach
    void setUp() {
        relationTypeRepository.deleteAll().block();
    }

    @Test
    void shouldCreateRelationType() {
        // Given
        RelationType relationType = new RelationType(
                "TREATS",
                "Doctor",
                "Patient",
                RelationSemantics.ASSOCIATIVE,
                false);

        // When & Then
        StepVerifier.create(relationTypeRepository.save(relationType))
                .assertNext(saved -> {
                    assertThat(saved.getName()).isEqualTo("TREATS");
                    assertThat(saved.getSemantics()).isEqualTo(RelationSemantics.ASSOCIATIVE);
                    assertThat(saved.isDirected()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldFindByName() {
        // Given
        RelationType relationType = new RelationType(
                "HAS_CONDITION",
                "Patient",
                "Condition",
                RelationSemantics.ASSOCIATIVE,
                true);
        relationTypeRepository.save(relationType).block();

        // When & Then
        StepVerifier.create(relationTypeRepository.findById("HAS_CONDITION"))
                .assertNext(found -> {
                    assertThat(found.getSemantics()).isEqualTo(RelationSemantics.ASSOCIATIVE);
                    assertThat(found.getSourceLabel()).isEqualTo("Patient");
                    assertThat(found.getTargetLabel()).isEqualTo("Condition");
                })
                .verifyComplete();
    }

    @Test
    void shouldFindBySemantics() {
        // Given
        relationTypeRepository.save(new RelationType(
                "PART_OF",
                "Component",
                "Whole",
                RelationSemantics.HIERARCHICAL,
                true)).block();

        relationTypeRepository.save(new RelationType(
                "CHILD_OF",
                "Child",
                "Parent",
                RelationSemantics.HIERARCHICAL,
                true)).block();

        relationTypeRepository.save(new RelationType(
                "TREATS",
                "Doctor",
                "Patient",
                RelationSemantics.ASSOCIATIVE,
                false)).block();

        // When & Then
        StepVerifier.create(relationTypeRepository.findBySemantics(RelationSemantics.HIERARCHICAL))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldCheckExistence() {
        // Given
        relationTypeRepository.save(new RelationType(
                "PRESCRIBES",
                "Doctor",
                "Medication",
                RelationSemantics.ASSOCIATIVE,
                true)).block();

        // When & Then
        StepVerifier.create(relationTypeRepository.existsByName("PRESCRIBES"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(relationTypeRepository.existsByName("NON_EXISTENT"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void shouldUpdateRelationType() {
        // Given - RelationType is immutable, so update = delete + insert
        RelationType original = new RelationType(
                "FOLLOWS",
                "Event",
                "Event",
                RelationSemantics.TEMPORAL,
                true);
        relationTypeRepository.save(original).block();

        // When - Delete old and create new (since it's immutable with same PK)
        relationTypeRepository.deleteById("FOLLOWS").block();
        RelationType updated = new RelationType(
                "FOLLOWS",
                "Event",
                "Event",
                RelationSemantics.TEMPORAL,
                false);

        // Then
        StepVerifier.create(relationTypeRepository.save(updated))
                .assertNext(saved -> {
                    assertThat(saved.getName()).isEqualTo("FOLLOWS");
                    assertThat(saved.isDirected()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteRelationType() {
        // Given
        RelationType relationType = new RelationType(
                "TEMPORARY",
                "Source",
                "Target",
                RelationSemantics.CUSTOM,
                true);
        relationTypeRepository.save(relationType).block();

        // When
        StepVerifier.create(relationTypeRepository.deleteById("TEMPORARY"))
                .verifyComplete();

        // Then
        StepVerifier.create(relationTypeRepository.findById("TEMPORARY"))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void shouldSaveRelationTypeWithMetadata() {
        // Given - Test canConnect validation (no metadata in immutable RelationType)
        RelationType relationType = new RelationType(
                "ANNOTATES",
                "Document",
                "Annotation",
                RelationSemantics.ASSOCIATIVE,
                true);

        // When & Then
        StepVerifier.create(relationTypeRepository.save(relationType))
                .assertNext(saved -> {
                    assertThat(saved.getName()).isEqualTo("ANNOTATES");
                    assertThat(saved.canConnect("Document", "Annotation")).isTrue();
                    assertThat(saved.canConnect("Annotation", "Document")).isFalse();
                })
                .verifyComplete();
    }

}
