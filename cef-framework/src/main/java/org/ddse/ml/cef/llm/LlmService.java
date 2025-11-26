package org.ddse.ml.cef.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * LLM integration service using Spring AI.
 * 
 * Provides unified interface for:
 * - OpenAI (default)
 * - Ollama (local)
 * - Custom models (via Spring AI)
 * 
 * Configuration via application.yml:
 * spring.ai.openai.api-key or spring.ai.ollama.base-url
 *
 * @author mrmanna
 */
@Service
@ConditionalOnBean(ChatClient.Builder.class)
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final ChatClient chatClient;

    public LlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Send prompt to LLM and get response.
     */
    public Mono<String> chat(String message) {
        log.debug("Sending message to LLM: {}", message.substring(0, Math.min(100, message.length())));

        return Mono.fromCallable(() -> chatClient.prompt()
                .user(message)
                .call()
                .content())
                .doOnSuccess(response -> log.debug("LLM response: {} chars", response.length()))
                .doOnError(e -> log.error("LLM call failed", e));
    }

    /**
     * Send prompt with system instructions.
     */
    public Mono<String> chatWithSystem(String systemMessage, String userMessage) {
        log.debug("Sending message to LLM with system context");

        return Mono.fromCallable(() -> chatClient.prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content())
                .doOnSuccess(response -> log.debug("LLM response: {} chars", response.length()))
                .doOnError(e -> log.error("LLM call failed", e));
    }

    /**
     * Generate structured response (JSON).
     */
    public <T> Mono<T> chatStructured(String message, Class<T> responseType) {
        log.debug("Requesting structured response of type: {}", responseType.getSimpleName());

        return Mono.fromCallable(() -> chatClient.prompt()
                .user(message)
                .call()
                .entity(responseType))
                .doOnSuccess(response -> log.debug("LLM structured response received"))
                .doOnError(e -> log.error("LLM structured call failed", e));
    }
}
