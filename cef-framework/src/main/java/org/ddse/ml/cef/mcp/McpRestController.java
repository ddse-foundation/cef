package org.ddse.ml.cef.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * REST endpoint for MCP tool (optional).
 * Enables external LLM systems to call CEF as HTTP service.
 * 
 * Enable via application.yml:
 * cef.mcp.rest.enabled: true
 *
 * @author mrmanna
 */
@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(name = "cef.mcp.rest.enabled", havingValue = "true", matchIfMissing = false)
public class McpRestController {

    private static final Logger log = LoggerFactory.getLogger(McpRestController.class);

    private final McpContextTool mcpTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpRestController(McpContextTool mcpTool) {
        this.mcpTool = mcpTool;
    }

    /**
     * GET /api/mcp/schema - Returns tool schema for LLM registration.
     */
    @GetMapping(value = "/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getSchema() {
        log.debug("Schema requested");
        try {
            Map<String, Object> schema = new HashMap<>();
            schema.put("name", mcpTool.getName());
            schema.put("description", mcpTool.getDescription());
            schema.put("parameters", objectMapper.readTree(mcpTool.getInputTypeSchema()));
            return schema;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate schema", e);
        }
    }

    /**
     * POST /api/mcp/invoke - Invoke MCP tool with retrieval request.
     */
    @PostMapping(value = "/invoke", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> invoke(@RequestBody Map<String, Object> request) {
        log.info("MCP REST invoked: {}", request);
        try {
            String json = objectMapper.writeValueAsString(request);
            return Mono.just(mcpTool.call(json));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * GET /api/mcp/health - Health check.
     */
    @GetMapping("/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of(
                "status", "UP",
                "service", "CEF MCP Tool"));
    }
}
