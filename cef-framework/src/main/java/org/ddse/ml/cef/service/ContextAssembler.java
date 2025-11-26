package org.ddse.ml.cef.service;

import org.ddse.ml.cef.domain.Chunk;
import org.ddse.ml.cef.domain.Edge;
import org.ddse.ml.cef.domain.Node;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Assembles retrieval results into a prompt context string within a strict
 * token budget.
 * Prioritizes high-value content (vector chunks) over secondary context (deep
 * graph nodes).
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Assemble context string respecting the token budget.
     *
     * @param result         The retrieval result containing nodes, edges, and
     *                       chunks.
     * @param maxTokenBudget The maximum number of tokens allowed.
     * @return Formatted context string.
     */
    public String assemble(RetrievalResult result, int maxTokenBudget) {
        log.info("=== [ContextAssembler] Assembling Context (Budget: {}) ===", maxTokenBudget);
        StringBuilder context = new StringBuilder();
        int currentTokens = 0;

        // 1. Header (Always include)
        String header = String.format("# Context Retrieval Result\n\n**Strategy:** %s\n**Retrieval Time:** %dms\n\n",
                result.getStrategy(), result.getRetrievalTimeMs());

        int headerTokens = estimateTokens(header);
        if (currentTokens + headerTokens > maxTokenBudget) {
            log.warn("Budget exceeded just by header! ({} > {})", headerTokens, maxTokenBudget);
            return header; // Should rarely happen
        }
        context.append(header);
        currentTokens += headerTokens;

        // 2. Vector Chunks (Highest Priority)
        if (!result.getChunks().isEmpty()) {
            String chunkHeader = "## Semantic Context\n\n";
            if (currentTokens + estimateTokens(chunkHeader) < maxTokenBudget) {
                context.append(chunkHeader);
                currentTokens += estimateTokens(chunkHeader);

                for (Chunk chunk : result.getChunks()) {
                    String chunkText = formatChunk(chunk);
                    int chunkTokens = estimateTokens(chunkText);

                    if (currentTokens + chunkTokens <= maxTokenBudget) {
                        context.append(chunkText);
                        currentTokens += chunkTokens;
                    } else {
                        log.warn("Budget exceeded at chunk {}. Stopping chunk assembly.", chunk.getId());
                        break;
                    }
                }
            }
        }

        // 3. Graph Context (Secondary Priority)
        if (!result.getNodes().isEmpty()) {
            String graphHeader = "\n## Graph Context\n\n";
            if (currentTokens + estimateTokens(graphHeader) < maxTokenBudget) {
                context.append(graphHeader);
                currentTokens += estimateTokens(graphHeader);

                // Nodes
                context.append("### Nodes\n");
                Set<String> includedNodeIds = new HashSet<>();

                for (Node node : result.getNodes()) {
                    String nodeText = formatNode(node);
                    int nodeTokens = estimateTokens(nodeText);

                    if (currentTokens + nodeTokens <= maxTokenBudget) {
                        context.append(nodeText);
                        currentTokens += nodeTokens;
                        includedNodeIds.add(node.getId().toString());
                    } else {
                        log.warn("Budget exceeded at node {}. Stopping node assembly.", node.getId());
                        break;
                    }
                }

                // Edges (Only if both source and target nodes are included)
                if (!result.getEdges().isEmpty() && currentTokens < maxTokenBudget) {
                    context.append("\n### Relationships\n");
                    for (Edge edge : result.getEdges()) {
                        if (includedNodeIds.contains(edge.getSourceNodeId().toString()) &&
                                includedNodeIds.contains(edge.getTargetNodeId().toString())) {

                            String edgeText = formatEdge(edge);
                            int edgeTokens = estimateTokens(edgeText);

                            if (currentTokens + edgeTokens <= maxTokenBudget) {
                                context.append(edgeText);
                                currentTokens += edgeTokens;
                            } else {
                                log.warn("Budget exceeded at edge {}. Stopping edge assembly.", edge.getId());
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (result.isEmpty()) {
            context.append("*No context found for the given query.*\n");
        }

        log.info("=== [ContextAssembler] Finished. Used: {}/{} tokens. ===", currentTokens, maxTokenBudget);
        return context.toString();
    }

    private String formatChunk(Chunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("### Chunk ").append(chunk.getId()).append("\n");
        sb.append(chunk.getContent()).append("\n\n");
        if (chunk.getMetadata() != null && !chunk.getMetadata().isEmpty()) {
            sb.append("*Metadata: ").append(chunk.getMetadata()).append("*\n\n");
        }
        return sb.toString();
    }

    private String formatNode(Node node) {
        StringBuilder sb = new StringBuilder();
        sb.append("- **").append(node.getLabel()).append("** (id: ")
                .append(node.getId()).append(")\n");
        if (node.getProperties() != null && !node.getProperties().isEmpty()) {
            sb.append("  Properties: ").append(node.getProperties()).append("\n");
        }
        return sb.toString();
    }

    private String formatEdge(Edge edge) {
        return String.format("- %s --[%s]--> %s\n",
                edge.getSourceNodeId(), edge.getRelationType(), edge.getTargetNodeId());
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }
}
