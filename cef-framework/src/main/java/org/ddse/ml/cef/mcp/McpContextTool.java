package org.ddse.ml.cef.mcp;

import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.retriever.RetrievalResult;
import org.ddse.ml.cef.service.ContextAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.springframework.ai.model.function.FunctionCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.ddse.ml.cef.dto.GraphQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP (Model Context Protocol) Tool implementation.
 * 
 * LLM calls this tool with dynamic schema based on configuration:
 * - textQuery: REQUIRED (always)
 * - graphQuery: REQUIRED (default)
 * - semanticKeywords: OPTIONAL (configurable via cef.mcp.required-fields)
 * 
 * Tool returns formatted context ready for LLM consumption.
 *
 * @author mrmanna
 */
@Service
public class McpContextTool implements FunctionCallback {

    private static final Logger log = LoggerFactory.getLogger(McpContextTool.class);

    private final KnowledgeRetriever knowledgeRetriever;
    private final CefProperties properties;
    private final ContextAssembler contextAssembler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpContextTool(KnowledgeRetriever knowledgeRetriever, CefProperties properties,
            ContextAssembler contextAssembler) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.properties = properties;
        this.contextAssembler = contextAssembler;
    }

    // --- FunctionCallback Implementation ---

    @Override
    public String getName() {
        return "retrieve_context";
    }

    @Override
    public String getDescription() {
        return "Retrieve context using Vector-First Resolution with optional Graph Patterns. " +
                "You MUST provide a 'graphQuery' with 'targets' to resolve specific entities in the Knowledge Graph. " +
                "The system resolves these targets first, then traverses the graph to enrich vector search results. " +
                "For multi-hop structured queries, you can specify 'patterns' in graphQuery to define explicit traversal paths with constraints. "
                +
                "Example pattern: Patient->DIAGNOSED_WITH->Disease->TREATED_WITH->Treatment with constraints on properties.";
    }

    @Override
    public String getInputTypeSchema() {
        try {
            return objectMapper.writeValueAsString(getToolSchema().get("parameters"));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tool schema", e);
        }
    }

    @Override
    public String call(String functionInput) {
        try {
            log.info("=== [MCP Tool] Called by LLM ===");
            log.info("Raw Function Input: {}", functionInput);

            JsonNode node = objectMapper.readTree(functionInput);

            RetrievalRequest.Builder builder = RetrievalRequest.builder();

            // Map 'textQuery' from schema to 'query' in record
            if (node.has("textQuery")) {
                builder.query(node.get("textQuery").asText());
            }

            if (node.has("graphQuery")) {
                builder.graphQuery(objectMapper.treeToValue(node.get("graphQuery"), GraphQuery.class));
            }

            if (node.has("semanticKeywords")) {
                List<String> keywords = new ArrayList<>();
                node.get("semanticKeywords").forEach(k -> keywords.add(k.asText()));
                builder.semanticKeywords(keywords);
            }

            if (node.has("topK"))
                builder.topK(node.get("topK").asInt());
            if (node.has("maxGraphNodes"))
                builder.maxGraphNodes(node.get("maxGraphNodes").asInt());
            if (node.has("maxTokenBudget"))
                builder.maxTokenBudget(node.get("maxTokenBudget").asInt());

            RetrievalRequest request = builder.build();
            log.info("Parsed RetrievalRequest: {}", request);

            // Invoke the reactive pipeline but block for the result since FunctionCallback
            // is synchronous
            return invoke(request).block();

        } catch (Exception e) {
            log.error("Failed to execute MCP tool call", e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * Main MCP tool endpoint called by LLM.
     * 
     * Returns context as structured JSON for LLM consumption.
     * 
     * NOTE: This method is private to enforce usage via the FunctionCallback
     * interface.
     * Do not make public or bypass the agent loop.
     * Use
     * {@link org.springframework.ai.model.function.FunctionCallback#call(String)}
     * instead.
     */
    public Mono<String> invoke(RetrievalRequest request) {
        log.info("MCP tool invoked with request: {}", request);

        // Validate required fields based on configuration
        validateRequest(request);

        // Apply defaults if not provided
        int maxTokenBudget = request.maxTokenBudget() > 0 ? request.maxTokenBudget()
                : properties.getMcp().getMaxTokenBudget();
        int maxGraphNodes = request.maxGraphNodes() > 0 ? request.maxGraphNodes()
                : properties.getMcp().getMaxGraphNodes();

        RetrievalRequest effectiveRequest = new RetrievalRequest(
                request.graphQuery(),
                request.query(),
                maxTokenBudget,
                request.topK(),
                maxGraphNodes,
                request.semanticKeywords());

        return knowledgeRetriever.retrieve(effectiveRequest)
                .map(result -> contextAssembler.assemble(result, effectiveRequest.maxTokenBudget()))
                .doOnSuccess(context -> log.debug("MCP tool returned {} characters", context.length()))
                .doOnError(e -> log.error("MCP tool failed", e));
    }

    /**
     * Get tool schema for LLM registration.
     * Schema is dynamic based on configuration.
     * 
     * NOTE: This method is private to enforce usage via the FunctionCallback
     * interface.
     * Do not make public.
     * Use
     * {@link org.springframework.ai.model.function.FunctionCallback#getInputTypeSchema()}
     * instead.
     */
    public Map<String, Object> getToolSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("name", "retrieve_context");
        schema.put("description", "Retrieve context using Vector-First Resolution. " +
                "You MUST provide a 'graphQuery' with 'targets' to resolve specific entities in the Knowledge Graph. " +
                "The system resolves these targets first, then traverses the graph to enrich vector search results.");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");

        Map<String, Object> propertiesMap = new HashMap<>();

        // textQuery - always required
        Map<String, Object> textQueryProp = new HashMap<>();
        textQueryProp.put("type", "string");
        textQueryProp.put("description", "Natural language query for semantic search");
        propertiesMap.put("textQuery", textQueryProp);

        // graphQuery - required by default
        Map<String, Object> graphQueryProp = new HashMap<>();
        graphQueryProp.put("type", "object");
        graphQueryProp.put("description", "Graph query for Vector-First Resolution. " +
                "Example: {targets: [{description: 'Patient John Doe', typeHint: 'Patient'}]}");

        Map<String, Object> graphQueryProperties = new HashMap<>();

        Map<String, Object> targetsProp = new HashMap<>();
        targetsProp.put("type", "array");
        targetsProp.put("description", "List of targets to resolve to entry points");

        Map<String, Object> targetItemProp = new HashMap<>();
        targetItemProp.put("type", "object");
        Map<String, Object> targetItemProperties = new HashMap<>();
        targetItemProperties.put("description", Map.of("type", "string", "description",
                "Description of the entity to resolve (e.g. 'Patient John Doe')"));
        targetItemProperties.put("typeHint",
                Map.of("type", "string", "description", "Optional type hint (e.g. 'Patient')"));
        targetItemProp.put("properties", targetItemProperties);
        targetItemProp.put("required", java.util.List.of("description"));

        targetsProp.put("items", targetItemProp);
        graphQueryProperties.put("targets", targetsProp);

        // Traversal hints
        Map<String, Object> traversalProp = new HashMap<>();
        traversalProp.put("type", "object");
        Map<String, Object> traversalProperties = new HashMap<>();
        traversalProperties.put("maxDepth", Map.of("type", "integer", "description", "Max traversal depth"));
        traversalProp.put("properties", traversalProperties);
        graphQueryProperties.put("traversal", traversalProp);

        // Patterns - optional structured graph patterns
        Map<String, Object> patternsProp = new HashMap<>();
        patternsProp.put("type", "array");
        patternsProp.put("description", "Optional graph patterns for structured multi-hop queries. " +
                "Each pattern defines a sequence of traversal steps with optional constraints. " +
                "Example: [{patternId: 'p1', steps: [{sourceLabel: 'Patient', relationType: 'DIAGNOSED_WITH', targetLabel: 'Disease', stepIndex: 0}], constraints: [], description: 'Patient diagnoses'}]");
        patternsProp.put("items", Map.of("type", "object"));
        graphQueryProperties.put("patterns", patternsProp);

        // Ranking strategy - optional
        Map<String, Object> rankingProp = new HashMap<>();
        rankingProp.put("type", "string");
        rankingProp.put("description",
                "Optional ranking strategy for patterns: PATH_LENGTH, EDGE_WEIGHT, NODE_CENTRALITY, SEMANTIC_SCORE, HYBRID");
        rankingProp.put("enum",
                java.util.List.of("PATH_LENGTH", "EDGE_WEIGHT", "NODE_CENTRALITY", "SEMANTIC_SCORE", "HYBRID"));
        graphQueryProperties.put("rankingStrategy", rankingProp);

        graphQueryProp.put("properties", graphQueryProperties);
        propertiesMap.put("graphQuery", graphQueryProp);

        // semanticKeywords - optional unless configured as required
        Map<String, Object> semanticKeywordsProp = new HashMap<>();
        semanticKeywordsProp.put("type", "array");
        semanticKeywordsProp.put("items", Map.of("type", "string"));
        semanticKeywordsProp.put("description", "Optional semantic keywords to boost vector search relevance");
        propertiesMap.put("semanticKeywords", semanticKeywordsProp);

        // topK - optional
        Map<String, Object> topKProp = new HashMap<>();
        topKProp.put("type", "integer");
        topKProp.put("description", "Number of results to return (default: 10)");
        topKProp.put("default", 10);
        propertiesMap.put("topK", topKProp);

        // maxTokenBudget - optional
        Map<String, Object> maxTokenBudgetProp = new HashMap<>();
        maxTokenBudgetProp.put("type", "integer");
        maxTokenBudgetProp.put("description",
                "Maximum token budget for context (default: " + properties.getMcp().getMaxTokenBudget() + ")");
        maxTokenBudgetProp.put("default", properties.getMcp().getMaxTokenBudget());
        propertiesMap.put("maxTokenBudget", maxTokenBudgetProp);

        // maxGraphNodes - optional
        Map<String, Object> maxGraphNodesProp = new HashMap<>();
        maxGraphNodesProp.put("type", "integer");
        maxGraphNodesProp.put("description",
                "Maximum number of graph nodes to traverse (default: " + properties.getMcp().getMaxGraphNodes() + ")");
        maxGraphNodesProp.put("default", properties.getMcp().getMaxGraphNodes());
        propertiesMap.put("maxGraphNodes", maxGraphNodesProp);

        parameters.put("properties", propertiesMap);
        parameters.put("required", properties.getMcp().getRequiredFields());

        schema.put("parameters", parameters);
        return schema;
    }

    /**
     * Validate request based on configured required fields.
     */
    private void validateRequest(RetrievalRequest request) {
        if (properties.getMcp().isFieldRequired("textQuery") &&
                (request.query() == null || request.query().isBlank())) {
            throw new IllegalArgumentException("textQuery is required");
        }

        if (properties.getMcp().isFieldRequired("graphQuery") && request.graphQuery() == null) {
            throw new IllegalArgumentException("graphQuery is required");
        }

        if (properties.getMcp().isFieldRequired("semanticKeywords") &&
                (request.semanticKeywords() == null || request.semanticKeywords().isEmpty())) {
            throw new IllegalArgumentException("semanticKeywords is required");
        }
    }
}
