package org.ddse.ml.cef.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Input schema for the CEF retrieve_context tool.
 * 
 * <p>This POJO defines the OpenAI-style function calling schema that Spring AI
 * serializes and sends to the LLM. Using a POJO with Jackson annotations ensures
 * proper JSON schema generation that LLMs can understand.</p>
 * 
 * <h3>Example LLM Tool Call (simple):</h3>
 * <pre>
 * {
 *   "textQuery": "What medications is patient John taking?",
 *   "targets": [
 *     {"description": "patient named John", "typeHint": "Patient"}
 *   ]
 * }
 * </pre>
 * 
 * <h3>Example LLM Tool Call (multi-target):</h3>
 * <pre>
 * {
 *   "textQuery": "Find drug interactions between medications for diabetic patients",
 *   "targets": [
 *     {"description": "diabetic patients", "typeHint": "Patient"},
 *     {"description": "diabetes medications", "typeHint": "Medication"}
 *   ],
 *   "relationTypes": ["PRESCRIBED_MEDICATION", "INTERACTS_WITH"]
 * }
 * </pre>
 * 
 * @author mrmanna
 * @since v0.6
 */
public record CefToolRequest(
    @JsonProperty(required = true)
    @JsonPropertyDescription("Natural language query for semantic search. This is the user's question.")
    String textQuery,
    
    @JsonPropertyDescription("List of entity targets to resolve in the knowledge graph. Each target has 'description' (what to search for) and optional 'typeHint' (entity type like Patient, Doctor, Medication).")
    List<Target> targets,
    
    @JsonPropertyDescription("How many relationship hops to traverse from found entities. Default is 2. Use higher values (3-4) for complex queries requiring multi-hop reasoning.")
    Integer traversalDepth,
    
    @JsonPropertyDescription("Maximum number of chunk results to return. Default is 5.")
    Integer topK,
    
    @JsonPropertyDescription("Optional list of relationship types to follow during graph traversal (e.g., ['TREATED_BY', 'HAS_CONDITION', 'PRESCRIBED_MEDICATION']). Leave empty to follow all relationships.")
    List<String> relationTypes
) {
    /**
     * Target entity for resolution.
     */
    public record Target(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Description of what to find (e.g., 'patient named John', 'diabetes medication', 'cardiac conditions')")
        String description,
        
        @JsonPropertyDescription("Entity type hint (e.g., 'Patient', 'Doctor', 'Medication', 'Condition'). Helps filter results to specific node types.")
        String typeHint
    ) {}
    
    /**
     * Builder for convenient construction - simple text query only.
     */
    public static CefToolRequest of(String textQuery) {
        return new CefToolRequest(textQuery, null, null, null, null);
    }
    
    /**
     * Builder for single target query.
     */
    public static CefToolRequest of(String textQuery, String entityDescription, String entityType) {
        return new CefToolRequest(
            textQuery, 
            List.of(new Target(entityDescription, entityType)), 
            2, 5, null
        );
    }
    
    /**
     * Builder for multi-target query.
     */
    public static CefToolRequest of(String textQuery, List<Target> targets) {
        return new CefToolRequest(textQuery, targets, 2, 5, null);
    }
}
