package org.ddse.ml.cef.repository.postgres;

import org.ddse.ml.cef.domain.Edge;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive repository for Edge entities.
 *
 * @author mrmanna
 */
@Repository
public interface EdgeRepository extends R2dbcRepository<Edge, UUID> {

        /**
         * Find edges by relation type.
         */
        Flux<Edge> findByRelationType(String relationType);

        /**
         * Find all edges from a source node.
         */
        @Query("SELECT * FROM edges WHERE source_node_id = :sourceNodeId")
        Flux<Edge> findBySourceNodeId(@Param("sourceNodeId") UUID sourceNodeId);

        /**
         * Find all edges to a target node.
         */
        @Query("SELECT * FROM edges WHERE target_node_id = :targetNodeId")
        Flux<Edge> findByTargetNodeId(@Param("targetNodeId") UUID targetNodeId);

        /**
         * Find all edges connected to a node (both incoming and outgoing).
         */
        @Query("SELECT * FROM edges WHERE source_node_id = :nodeId OR target_node_id = :nodeId")
        Flux<Edge> findByNodeId(@Param("nodeId") UUID nodeId);

        /**
         * Find edges by relation type from a specific source node.
         */
        @Query("SELECT * FROM edges WHERE source_node_id = :sourceNodeId AND relation_type = :relationType")
        Flux<Edge> findBySourceNodeIdAndRelationType(@Param("sourceNodeId") UUID sourceNodeId,
                        @Param("relationType") String relationType);

        /**
         * Check if edge exists between two nodes with specific relation type.
         */
        @Query("SELECT COUNT(*) > 0 FROM edges WHERE source_node_id = :sourceNodeId " +
                        "AND target_node_id = :targetNodeId AND relation_type = :relationType")
        Mono<Boolean> existsBySourceAndTargetAndType(@Param("sourceNodeId") UUID sourceNodeId,
                        @Param("targetNodeId") UUID targetNodeId,
                        @Param("relationType") String relationType);

        /**
         * Delete edges by relation type (bulk cleanup).
         */
        @Query("DELETE FROM edges WHERE relation_type = :relationType")
        Mono<Void> deleteByRelationType(@Param("relationType") String relationType);

        /**
         * Delete all edges connected to a node.
         */
        @Query("DELETE FROM edges WHERE source_node_id = :nodeId OR target_node_id = :nodeId")
        Mono<Void> deleteByNodeId(@Param("nodeId") UUID nodeId);
}
