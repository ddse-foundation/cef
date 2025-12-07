package org.ddse.ml.cef.llm;

import org.ddse.ml.cef.CefTestApplication;
import org.ddse.ml.cef.api.KnowledgeRetriever;
import org.ddse.ml.cef.config.CefProperties;
import org.ddse.ml.cef.config.DuckDbTestConfiguration;
import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.ddse.ml.cef.base.MedicalDataTestBase;
import org.ddse.ml.cef.service.ContextAssembler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CefAgentService with real LLM tool calling.
 * 
 * <p>This test verifies the complete flow:</p>
 * <ol>
 *   <li>CefAgentService sends prompt + tool schema to LLM</li>
 *   <li>LLM decides to call retrieve_context tool</li>
 *   <li>Spring AI deserializes tool call JSON to CefToolRequest</li>
 *   <li>CefToolFunction executes and returns context</li>
 *   <li>LLM generates final response with context</li>
 * </ol>
 */
@SpringBootTest(classes = CefTestApplication.class, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "cef.graph.store=duckdb",
        "cef.vector.store=duckdb"
})
@Import({ DuckDbTestConfiguration.class, VllmTestConfiguration.class })
@ActiveProfiles({ "vllm-integration", "duckdb" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CEF Agent LLM Tool Calling Tests")
class CefAgentServiceIT extends MedicalDataTestBase {

    private static final Logger log = LoggerFactory.getLogger(CefAgentServiceIT.class);

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    @Autowired
    private ContextAssembler contextAssembler;

    @Autowired
    private CefProperties cefProperties;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    // Manually created components (not autowired due to bean creation order)
    private CefToolFunction toolFunction;
    private FunctionCallback retrieveContextTool;

    @BeforeAll
    void setupTool() {
        // Manually create the tool function and callback
        toolFunction = new CefToolFunction(knowledgeRetriever, contextAssembler, cefProperties);
        
        retrieveContextTool = FunctionCallbackWrapper.builder(toolFunction)
                .withName("retrieve_context")
                .withDescription("Retrieve context from the knowledge graph using semantic search and graph traversal. " +
                        "Use this tool when you need to find information about entities, their relationships, " +
                        "or answer questions that require knowledge from the database.")
                .withInputType(CefToolRequest.class)
                .build();
        
        log.info("Tool created: {}", retrieveContextTool.getName());
    }

    @Test
    @DisplayName("Tool schema should be properly generated from CefToolRequest")
    void shouldGenerateProperToolSchema() {
        log.info("=== Tool Schema ===");
        log.info("Name: {}", retrieveContextTool.getName());
        log.info("Description: {}", retrieveContextTool.getDescription());
        log.info("Input Schema:\n{}", retrieveContextTool.getInputTypeSchema());
        
        assertThat(retrieveContextTool.getName()).isEqualTo("retrieve_context");
        assertThat(retrieveContextTool.getDescription()).contains("knowledge graph");
        
        String schema = retrieveContextTool.getInputTypeSchema();
        assertThat(schema).contains("textQuery");
        assertThat(schema).contains("targets");
    }

    @Test
    @DisplayName("Tool function should work with direct invocation")
    void shouldExecuteToolDirectly() {
        // Test direct invocation (bypassing LLM)
        CefToolRequest request = new CefToolRequest(
                "Find patients with diabetes",
                List.of(new CefToolRequest.Target("diabetic patients", "Patient")),
                2,
                5,
                null
        );
        
        String result = toolFunction.apply(request);
        
        log.info("=== Direct Tool Result ===");
        log.info("Result length: {} chars", result.length());
        log.info("Result preview: {}", result.substring(0, Math.min(500, result.length())));
        
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("Agent should answer question using tool via LLM")
    void shouldAnswerQuestionUsingTool() {
        ChatClient chatClient = chatClientBuilder
                .defaultFunctions(retrieveContextTool)
                .build();

        String systemPrompt = """
            You are a helpful assistant with access to a knowledge retrieval tool.
            Use the retrieve_context tool to find information before answering questions.
            """;

        String userQuery = "What conditions does patient PT-10001 have?";
        
        log.info("=== Sending to LLM ===");
        log.info("User Query: {}", userQuery);
        
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userQuery)
                .call()
                .content();
        
        log.info("=== Agent Response ===");
        log.info("Response: {}", response);
        
        assertThat(response).isNotBlank();
    }
}
