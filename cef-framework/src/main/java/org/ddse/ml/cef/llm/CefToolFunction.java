package org.ddse.ml.cef.llm;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.service.ContextAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CEF Tool Function for OpenAI-style tool calling.
 * 
 * <p>This function is registered with Spring AI's FunctionCallbackWrapper to enable
 * LLM tool calling. When the LLM decides to call the retrieve_context tool, Spring AI
 * deserializes the JSON arguments into {@link CefToolRequest} and invokes this function.</p>
 * 
 * <h3>Architecture Flow:</h3>
 * <pre>
 * User Query → LLM → Tool Call Decision → Spring AI deserializes JSON → CefToolFunction.apply()
 *                                                                              ↓
 *                                                                    KnowledgeRetriever.retrieve()
 *                                                                              ↓
 *                                                                    ContextAssembler.assemble()
 *                                                                              ↓
 *                                                                    Formatted Context String → LLM
 * </pre>
 * 
 * @author mrmanna
 * @since v0.6
 */
@Component
public class CefToolFunction implements Function<CefToolRequest, String> {

    private static final Logger log = LoggerFactory.getLogger(CefToolFunction.class);

    private final KnowledgeRetriever knowledgeRetriever;
    private final ContextAssembler contextAssembler;
    private final CefProperties properties;

    public CefToolFunction(KnowledgeRetriever knowledgeRetriever,
                           ContextAssembler contextAssembler,
                           CefProperties properties) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.contextAssembler = contextAssembler;
        this.properties = properties;
    }

    @Override
    public String apply(CefToolRequest request) {
        log.info("=== [CEF Tool] Called by LLM ===");
        log.info("Request: textQuery='{}', targets={}", 
                request.textQuery(), 
                request.targets() != null ? request.targets().size() + " targets" : "none");
        
        if (request.targets() != null) {
            request.targets().forEach(t -> 
                log.info("  Target: description='{}', typeHint='{}'", t.description(), t.typeHint()));
        }

        try {
            // Convert CefToolRequest to RetrievalRequest
            RetrievalRequest retrievalRequest = buildRetrievalRequest(request);
            log.debug("Built RetrievalRequest: {}", retrievalRequest);

            // Execute retrieval
            var result = knowledgeRetriever.retrieve(retrievalRequest).block();
            
            if (result == null) {
                log.warn("Retrieval returned null result");
                return "No context found for the query.";
            }

            // Assemble context for LLM
            int maxTokenBudget = properties.getMcp().getMaxTokenBudget();
            
            String context = contextAssembler.assemble(result, maxTokenBudget);
            
            log.info("=== [CEF Tool] Returning {} characters of context, {} nodes, {} chunks ===", 
                    context.length(),
                    result.getNodes() != null ? result.getNodes().size() : 0,
                    result.getChunks() != null ? result.getChunks().size() : 0);
            return context;

        } catch (Exception e) {
            log.error("CEF Tool execution failed", e);
            return "Error retrieving context: " + e.getMessage();
        }
    }

    /**
     * Convert the simple CefToolRequest to the full RetrievalRequest.
     */
    private RetrievalRequest buildRetrievalRequest(CefToolRequest request) {
        RetrievalRequest.Builder builder = RetrievalRequest.builder()
                .query(request.textQuery())
                .topK(request.topK() != null ? request.topK() : 5);

        // Build GraphQuery if targets are provided
        if (request.targets() != null && !request.targets().isEmpty()) {
            // Convert CefToolRequest.Target to ResolutionTarget
            List<ResolutionTarget> resolutionTargets = request.targets().stream()
                    .map(t -> new ResolutionTarget(
                            t.description(),
                            t.typeHint(),
                            Map.of()
                    ))
                    .collect(Collectors.toList());

            int depth = request.traversalDepth() != null ? request.traversalDepth() : 2;
            TraversalHint traversal = new TraversalHint(
                    depth,
                    request.relationTypes(),
                    null
            );

            GraphQuery graphQuery = new GraphQuery(
                    resolutionTargets,
                    traversal,
                    null,  // patterns
                    null,  // combinator
                    null   // rankingStrategy
            );

            builder.graphQuery(graphQuery);
            builder.maxGraphNodes(properties.getMcp().getMaxGraphNodes());
            
            log.debug("Built GraphQuery with {} targets, depth={}", resolutionTargets.size(), depth);
        }

        return builder.build();
    }
}
