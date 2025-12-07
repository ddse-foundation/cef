package org.ddse.ml.cef.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ddse.ml.cef.service.DefaultEmbeddingService;
import org.ddse.ml.cef.service.EmbeddingService;
import org.ddse.ml.cef.service.ResilientEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for CEF resilience patterns.
 * 
 * When cef.resilience.enabled=true:
 * - ResilientEmbeddingService wraps embedding calls with retry/circuit-breaker/timeout
 * - Metrics are exported to configured registry
 * 
 * When cef.resilience.enabled=false (default):
 * - DefaultEmbeddingService is used (no resilience overhead)
 * - Suitable for development and testing
 *
 * @author mrmanna
 * @since 0.6
 */
@Configuration
@EnableConfigurationProperties({CefProperties.class, CefResilienceProperties.class})
public class CefResilienceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CefResilienceAutoConfiguration.class);

    /**
     * Provide a default MeterRegistry if none is configured.
     * In production, spring-boot-starter-actuator provides a real registry.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry simpleMeterRegistry() {
        log.info("No MeterRegistry configured, using SimpleMeterRegistry (metrics will not be exported)");
        return new SimpleMeterRegistry();
    }

    /**
     * Resilient embedding service with retry, circuit breaker, and timeout.
     * Activated when cef.resilience.enabled=true.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "cef.resilience.enabled", havingValue = "true")
    public EmbeddingService resilientEmbeddingService(
            EmbeddingModel embeddingModel,
            CefResilienceProperties resilienceProperties,
            MeterRegistry meterRegistry) {
        log.info("Configuring ResilientEmbeddingService (production mode)");
        return new ResilientEmbeddingService(embeddingModel, resilienceProperties, meterRegistry);
    }

    /**
     * Default embedding service without resilience patterns.
     * Used when cef.resilience.enabled=false (development mode).
     */
    @Bean
    @ConditionalOnProperty(name = "cef.resilience.enabled", havingValue = "false", matchIfMissing = true)
    public EmbeddingService defaultEmbeddingService(EmbeddingModel embeddingModel) {
        log.info("Configuring DefaultEmbeddingService (development mode - no resilience)");
        return new DefaultEmbeddingService(embeddingModel);
    }
}
