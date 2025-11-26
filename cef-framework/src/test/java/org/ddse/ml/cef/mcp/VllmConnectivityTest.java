package org.ddse.ml.cef.mcp;

import org.ddse.ml.cef.config.VllmTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple connectivity test for vLLM integration.
 * Verifies that Spring AI can talk to the vLLM server running on
 * localhost:8001.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VllmTestConfiguration.class)
class VllmConnectivityTest {

    private static final Logger logger = LoggerFactory.getLogger(VllmConnectivityTest.class);

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    @DisplayName("Should receive response from vLLM server using ChatClient")
    void shouldReceiveResponseFromVllm() {
        logger.info("Starting vLLM connectivity test with ChatClient...");

        ChatClient client = chatClientBuilder.build();
        String prompt = "Hello, are you working?";

        logger.info("Sending prompt: {}", prompt);

        String response = client.prompt()
                .user(prompt)
                .call()
                .content();

        logger.info("Received response: {}", response);

        assertThat(response).isNotBlank();
    }
}
