package org.ddse.ml.cef.retriever;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.repository.ChunkStore;
import org.ddse.ml.cef.service.KnowledgeRetrieverImpl;
import org.ddse.ml.cef.storage.GraphStore;
import org.ddse.ml.cef.storage.GraphSubgraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KnowledgeRetrieverImpl with mocked dependencies.
 * Tests retrieval strategies without real LLM/embedding services.
 *
 * @author mrmanna
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeRetrieverTest {

    @Mock
    private GraphStore graphStore;

    @Mock
    private ChunkStore chunkStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private CefProperties properties;
    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp() {
        properties = new CefProperties();
        properties.getVector().setStore("postgres");
        retriever = new KnowledgeRetrieverImpl(graphStore, chunkStore, embeddingModel, properties);
    }

    /**
     * Helper method to setup embedding model mock to return proper
     * EmbeddingResponse.
     */
    private void setupEmbeddingMock(String query, float[] embedding) {
        Embedding mockEmbedding = new Embedding(embedding, 0);
        EmbeddingResponse mockResponse = new EmbeddingResponse(List.of(mockEmbedding));
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenReturn(mockResponse);
    }

    @Test
    void shouldFindNodesByProperties() {
        // Given
        Node node1 = createNode(UUID.randomUUID(), "Patient", Map.of("age", 65, "status", "active"));
        Node node2 = createNode(UUID.randomUUID(), "Patient", Map.of("age", 45, "status", "active"));
        Node node3 = createNode(UUID.randomUUID(), "Patient", Map.of("age", 70, "status", "inactive"));

        when(graphStore.findNodesByLabel("Patient"))
                .thenReturn(Flux.just(node1, node2, node3));

        // When & Then - filter by status
        StepVerifier.create(retriever.findNodesByProperties("Patient", "status", "active"))
                .assertNext(nodes -> {
                    assertThat(nodes).hasSize(2);
                    assertThat(nodes).extracting(n -> n.getProperties().get("status"))
                            .containsOnly("active");
                })
                .verifyComplete();
    }

    @Test
    void shouldExpandFromSeeds() {
        // Given
        UUID seed1 = UUID.randomUUID();
        UUID seed2 = UUID.randomUUID();
        Node node1 = createNode(seed1, "Patient", Map.of());
        Node node2 = createNode(seed2, "Doctor", Map.of());
        Edge edge = createEdge(seed1, seed2, "TREATED_BY");

        GraphSubgraph subgraph = new GraphSubgraph(
                List.of(node1, node2),
                List.of(edge));

        when(graphStore.extractSubgraph(List.of(seed1, seed2), 2))
                .thenReturn(Mono.just(subgraph));

        // When & Then
        StepVerifier.create(retriever.expandFromSeeds(List.of(seed1, seed2), 2, null))
                .assertNext(result -> {
                    assertThat(result.getNodes()).hasSize(2);
                    assertThat(result.getEdges()).hasSize(1);
                    assertThat(result.getStrategy()).isEqualTo(RetrievalResult.RetrievalStrategy.EXPANSION);
                })
                .verifyComplete();
    }

    @Test
    void shouldFilterEdgesByRelationTypesInExpansion() {
        // Given
        UUID seed = UUID.randomUUID();
        Node node1 = createNode(seed, "Patient", Map.of());
        Node node2 = createNode(UUID.randomUUID(), "Doctor", Map.of());
        Node node3 = createNode(UUID.randomUUID(), "Hospital", Map.of());

        Edge edge1 = createEdge(seed, node2.getId(), "TREATED_BY");
        Edge edge2 = createEdge(seed, node3.getId(), "ADMITTED_TO");

        GraphSubgraph subgraph = new GraphSubgraph(
                List.of(node1, node2, node3),
                List.of(edge1, edge2));

        when(graphStore.extractSubgraph(List.of(seed), 2))
                .thenReturn(Mono.just(subgraph));

        // When & Then - filter only TREATED_BY
        StepVerifier.create(retriever.expandFromSeeds(List.of(seed), 2, List.of("TREATED_BY")))
                .assertNext(result -> {
                    assertThat(result.getNodes()).hasSize(3);
                    assertThat(result.getEdges()).hasSize(1);
                    assertThat(result.getEdges().get(0).getRelationType()).isEqualTo("TREATED_BY");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleRetrievalRequestWithGraphQuery() {
        // Given
        GraphQuery graphQuery = new GraphQuery(
                List.of(new ResolutionTarget("John Doe", "Patient", Map.of())),
                new TraversalHint(2, null, null));

        RetrievalRequest request = RetrievalRequest.builder()
                .query("patient data")
                .topK(10)
                .graphQuery(graphQuery)
                .build();

        UUID nodeId = UUID.randomUUID();
        Node patientNode = createNode(nodeId, "Patient", Map.of("name", "John Doe"));
        GraphSubgraph subgraph = new GraphSubgraph(List.of(patientNode), List.of());

        Chunk chunk = createChunk(UUID.randomUUID(), "John Doe record", 0.9f);
        chunk.setLinkedNodeId(nodeId);

        float[] mockEmbedding = new float[768];
        setupEmbeddingMock("John Doe", mockEmbedding);

        when(chunkStore.findTopKSimilar(mockEmbedding, 10)).thenReturn(Flux.just(chunk));
        when(graphStore.extractSubgraph(List.of(nodeId), 2)).thenReturn(Mono.just(subgraph));

        // When & Then
        StepVerifier.create(retriever.retrieve(request))
                .assertNext(result -> {
                    assertThat(result.getStrategy()).isEqualTo(RetrievalResult.RetrievalStrategy.GRAPH_ONLY);
                    assertThat(result.getRetrievalTimeMs()).isGreaterThan(0);
                    assertThat(result.getNodes()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    void shouldFallbackToVectorWhenGraphQueryYieldsNoResults() {
        // Given
        GraphQuery graphQuery = new GraphQuery(
                List.of(new ResolutionTarget("Unknown", null, Map.of())),
                new TraversalHint(2, null, null));

        RetrievalRequest request = RetrievalRequest.builder()
                .query("test query")
                .topK(5)
                .graphQuery(graphQuery)
                .build();

        Chunk chunk = createChunk(UUID.randomUUID(), "fallback content", 0.75f);
        float[] mockEmbedding = new float[768];

        // Setup embedding mock to return the same embedding for any query
        setupEmbeddingMock("any", mockEmbedding);

        // When chunkStore is called with any embedding, return the chunk
        // Note: For resolveEntryPoints, this chunk has null linkedNodeId, so it will be
        // filtered out, resulting in empty startNodes.
        // For retrieveFromVectorStore, this chunk is returned as result.
        when(chunkStore.findTopKSimilar(any(float[].class), eq(5))).thenReturn(Flux.just(chunk));

        // When & Then
        StepVerifier.create(retriever.retrieve(request))
                .assertNext(result -> {
                    assertThat(result.getChunks()).hasSize(1);
                    assertThat(result.getStrategy()).isEqualTo(RetrievalResult.RetrievalStrategy.VECTOR_ONLY);
                    assertThat(result.getRetrievalTimeMs()).isGreaterThan(0);
                })
                .verifyComplete();
    }

    // Helper methods

    private Node createNode(UUID id, String label, Map<String, Object> properties) {
        Node node = new Node();
        node.setId(id);
        node.setLabel(label);
        node.setProperties(properties);
        return node;
    }

    private Edge createEdge(UUID sourceId, UUID targetId, String relationType) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID());
        edge.setSourceNodeId(sourceId);
        edge.setTargetNodeId(targetId);
        edge.setRelationType(relationType);
        return edge;
    }

    private Chunk createChunk(UUID id, String text, float score) {
        // Chunk doesn't have score field - it's calculated during search
        // Use content for text, embedding for vector representation
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setContent(text);
        chunk.setEmbedding(new float[768]); // Mock embedding
        return chunk;
    }
}
