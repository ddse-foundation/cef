package org.ddse.ml.cef.retriever;

import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.RetrievalRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Interface for retrieving context from the knowledge graph and vector store.
 * 
 * Implements 3-level fallback strategy:
 * 1. Graph-only retrieval (when graph hints provided)
 * 2. Hybrid retrieval (semantic search + graph traversal)
 * 3. Vector-only retrieval (pure semantic search fallback)
 * 
 * Used by MCP tool to assemble context for LLM.
 *
 * @author mrmanna
 */
public interface ContextRetriever {

    /**
     * Retrieve context using full hybrid approach.
     * 
     * Strategy:
     * 1. If graphQuery provided -> Vector-First Resolution
     * 2. Else -> semantic search for seed nodes
     * 3. Extract subgraph around seeds (k-hop neighbors)
     * 4. Find related chunks via vector similarity
     * 5. Merge and rank by relevance
     * 
     * @param request Retrieval request with query and optional hints
     * @return Context with nodes, edges, chunks ranked by relevance
     */
    Mono<RetrievalResult> retrieveContext(RetrievalRequest request);

    /**
     * Graph-only retrieval (when user provides explicit graph hints).
     * 
     * Example: "Find all patients treated by Dr. Smith"
     * GraphHints: {startNodeLabels: ["Doctor"], relationTypes: ["TREATS"]}
     */
    Mono<RetrievalResult> retrieveFromGraph(GraphRetrievalRequest request);

    /**
     * Vector-only retrieval (fallback when graph traversal yields no results).
     * Pure semantic search without graph context.
     */
    Mono<RetrievalResult> retrieveFromVectorStore(VectorRetrievalRequest request);

    /**
     * Find nodes by property values.
     * Useful for filtering graph by user-defined properties.
     * 
     * Example: Find all patients with age > 60
     */
    Mono<List<Node>> findNodesByProperties(String label, String propertyName, Object propertyValue);

    /**
     * Expand from seed nodes using graph traversal.
     * 
     * @param seedNodeIds   Starting nodes
     * @param depth         Maximum hops (1-3 typical)
     * @param relationTypes Optional: filter by relation types
     */
    Mono<RetrievalResult> expandFromSeeds(List<UUID> seedNodeIds, int depth, List<String> relationTypes);
}
