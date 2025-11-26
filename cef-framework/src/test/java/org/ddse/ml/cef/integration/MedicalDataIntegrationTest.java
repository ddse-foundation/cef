package org.ddse.ml.cef.integration;

import org.ddse.ml.cef.DuckDBTestConfiguration;
import org.ddse.ml.cef.base.MedicalDataTestBase;
import org.ddse.ml.cef.dto.GraphQuery;
import org.ddse.ml.cef.dto.ResolutionTarget;
import org.ddse.ml.cef.dto.RetrievalRequest;
import org.ddse.ml.cef.dto.TraversalHint;
import org.ddse.ml.cef.mcp.McpContextTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that loads the medical benchmark dataset into DuckDB and
 * exercises the retrieval pipeline via the MCP tool.
 */
@SpringBootTest(classes = DuckDBTestConfiguration.class, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.test.mockmvc.enabled=false"
})
@ActiveProfiles("duckdb")
class MedicalDataIntegrationTest extends MedicalDataTestBase {

    @Autowired
    private McpContextTool mcpTool;

    @Test
    @DisplayName("MCP retrieval returns context for structured graph query")
    void shouldRetrieveContextWithGraphHints() {
        var request = RetrievalRequest.builder()
                .query("Find patients with Bronchial Asthma")
                .graphQuery(new GraphQuery(
                        java.util.List.of(new ResolutionTarget("Bronchial Asthma patient", "Patient", Map.of())),
                        new TraversalHint(2, null, null)))
                .topK(5)
                .maxTokenBudget(1000)
                .build();

        String context = mcpTool.invoke(request).block();
        assertThat(context).isNotBlank();
        assertThat(context).containsIgnoringCase("Context Retrieval Result");
    }

    @Test
    @DisplayName("Vector-only retrieval returns semantic context")
    void shouldRetrieveVectorOnlyContext() {
        var request = RetrievalRequest.builder()
                .query("diabetes treatment options")
                .topK(5)
                .maxTokenBudget(1000)
                .build();

        String context = mcpTool.invoke(request).block();
        assertThat(context).isNotBlank();
        assertThat(context).containsIgnoringCase("Semantic Context");
    }
}
