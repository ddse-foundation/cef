package org.ddse.ml.cef.api;

import org.ddse.ml.cef.domain.Direction;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.retriever.RetrievalResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Primary interface for retrieving knowledge from the CEF framework.
 * 
 * Unified interface for:
 * 1. Hybrid Retrieval (Vector + Graph) via retrieve()
 * 2. Direct Graph Lookups (findNode, findByLabel)
 * 3. Graph Traversal (findRelated, expandFromSeeds)
 * 
 * This is the ONLY interface clients and tests should use for retrieval.
 */
public interface KnowledgeRetriever {

    /**
     * Main entry point for Hybrid Retrieval.
     * Executes the optimal strategy (Vector-First, Graph-Only, or Hybrid) based on
     * the request.
     */
    Mono<RetrievalResult> retrieve(RetrievalRequest request);

    Mono<Node> findNode(UUID id);

    Flux<Node> findByLabel(String label);

    Flux<Node> findRelated(UUID id, String relationType, Direction direction);

    /**
     * Find nodes by specific property value.
     */
    Mono<List<Node>> findNodesByProperties(String label, String propertyName, Object propertyValue);

    /**
     * Expand context from seed nodes.
     */
    Mono<RetrievalResult> expandFromSeeds(List<UUID> seedNodeIds, int depth, List<String> relationTypes);
}
