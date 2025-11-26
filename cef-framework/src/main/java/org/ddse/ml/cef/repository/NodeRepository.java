package org.ddse.ml.cef.repository;

import org.ddse.ml.cef.domain.Node;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for Node entities.
 * R2DBC provides non-blocking database operations for high throughput.
 *
 * @author mrmanna
 */
@Repository
public interface NodeRepository extends R2dbcRepository<Node, UUID> {

    /**
     * Find nodes by label.
     */
    Flux<Node> findByLabel(String label);

    /**
     * Find nodes by label with pagination.
     */
    @Query("SELECT * FROM nodes WHERE label = :label ORDER BY created DESC LIMIT :limit OFFSET :offset")
    Flux<Node> findByLabelPaginated(@Param("label") String label,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * Count nodes by label.
     */
    @Query("SELECT COUNT(*) FROM nodes WHERE label = :label")
    Mono<Long> countByLabel(@Param("label") String label);

    /**
     * Find nodes with vectorizable content (for auto-embedding).
     */
    @Query("SELECT * FROM nodes WHERE vectorizable_content IS NOT NULL")
    Flux<Node> findNodesWithVectorizableContent();

    /**
     * Delete nodes by label (bulk cleanup).
     */
    @Query("DELETE FROM nodes WHERE label = :label")
    Mono<Void> deleteByLabel(@Param("label") String label);
}
