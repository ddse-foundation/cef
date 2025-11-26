package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.RelationSemantics;
import org.ddse.ml.cef.domain.RelationType;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for RelationType entities.
 */
@Repository
public interface RelationTypeRepository extends R2dbcRepository<RelationType, String> {

    /**
     * Find relation types by semantics.
     */
    Flux<RelationType> findBySemantics(RelationSemantics semantics);

    /**
     * Check if a relation type with the given name exists.
     */
    Mono<Boolean> existsByName(String name);
}
