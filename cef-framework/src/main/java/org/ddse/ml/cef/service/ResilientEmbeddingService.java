package org.ddse.ml.cef.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.ddse.ml.cef.config.CefResilienceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Resilient implementation of EmbeddingService with production-grade patterns.
 * 
 * Features:
 * - Retry with exponential backoff for transient failures
 * - Circuit breaker to prevent cascade failures
 * - Timeout to prevent indefinite blocking
 * - Metrics for observability
 * 
 * Activated when cef.resilience.enabled=true (production profile).
 * Falls back to DefaultEmbeddingService when disabled.
 *
 * @author mrmanna
 * @since 0.6
 */
public class ResilientEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ResilientEmbeddingService.class);
    private static final String SERVICE_NAME = "embedding";

    private final EmbeddingModel embeddingModel;
    private final CefResilienceProperties.ServiceResilienceConfig config;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final Duration timeout;

    // Metrics
    private final Timer embeddingTimer;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter retryCounter;
    private final Counter circuitBreakerOpenCounter;
    private final Counter timeoutCounter;

    public ResilientEmbeddingService(
            EmbeddingModel embeddingModel,
            CefResilienceProperties resilienceProperties,
            MeterRegistry meterRegistry) {
        
        this.embeddingModel = embeddingModel;
        this.config = resilienceProperties.getEmbedding();
        this.timeout = config.getTimeout();

        // Configure Retry
        this.retry = createRetry(config.getRetry());

        // Configure Circuit Breaker
        this.circuitBreaker = createCircuitBreaker(config.getCircuitBreaker());

        // Configure Metrics
        this.embeddingTimer = Timer.builder("cef.embedding.duration")
                .description("Time to generate embeddings")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);

        this.successCounter = Counter.builder("cef.embedding.calls")
                .description("Embedding call results")
                .tag("service", SERVICE_NAME)
                .tag("outcome", "success")
                .register(meterRegistry);

        this.failureCounter = Counter.builder("cef.embedding.calls")
                .description("Embedding call results")
                .tag("service", SERVICE_NAME)
                .tag("outcome", "failure")
                .register(meterRegistry);

        this.retryCounter = Counter.builder("cef.embedding.retries")
                .description("Embedding retry attempts")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);

        this.circuitBreakerOpenCounter = Counter.builder("cef.embedding.circuit_breaker_open")
                .description("Circuit breaker open events")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);

        this.timeoutCounter = Counter.builder("cef.embedding.timeouts")
                .description("Embedding timeout events")
                .tag("service", SERVICE_NAME)
                .register(meterRegistry);

        log.info("ResilientEmbeddingService initialized with retry={}, circuitBreaker={}, timeout={}",
                config.getRetry().isEnabled(),
                config.getCircuitBreaker().isEnabled(),
                timeout);
    }

    @Override
    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.error(new IllegalArgumentException("Text cannot be null or blank"));
        }

        return Mono.fromCallable(() -> {
                    Timer.Sample sample = Timer.start();
                    try {
                        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
                        float[] result = response.getResults().get(0).getOutput();
                        sample.stop(embeddingTimer);
                        return result;
                    } catch (Exception e) {
                        sample.stop(embeddingTimer);
                        throw e;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transform(this::applyResilience)
                .doOnSuccess(v -> successCounter.increment())
                .doOnError(e -> {
                    failureCounter.increment();
                    if (e instanceof TimeoutException) {
                        timeoutCounter.increment();
                    }
                    log.error("Embedding failed for text (length={}): {}", 
                            text.length(), e.getMessage());
                });
    }

    @Override
    public Mono<List<float[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.error(new IllegalArgumentException("Texts list cannot be null or empty"));
        }

        return Mono.fromCallable(() -> {
                    Timer.Sample sample = Timer.start();
                    try {
                        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
                        List<float[]> results = response.getResults().stream()
                                .map(result -> result.getOutput())
                                .collect(Collectors.toList());
                        sample.stop(embeddingTimer);
                        return results;
                    } catch (Exception e) {
                        sample.stop(embeddingTimer);
                        throw e;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .transform(this::applyResilience)
                .doOnSuccess(v -> successCounter.increment())
                .doOnError(e -> {
                    failureCounter.increment();
                    log.error("Batch embedding failed for {} texts: {}", 
                            texts.size(), e.getMessage());
                });
    }

    /**
     * Apply resilience patterns: timeout -> retry -> circuit breaker.
     * Order matters: timeout applies first, then retry wraps that, 
     * then circuit breaker wraps everything.
     */
    private <T> Mono<T> applyResilience(Mono<T> mono) {
        Mono<T> result = mono;

        // Apply timeout first
        result = result.timeout(timeout)
                .doOnError(TimeoutException.class, e -> 
                    log.warn("Embedding call timed out after {}", timeout));

        // Apply retry (wraps timeout)
        if (config.getRetry().isEnabled()) {
            result = result.transformDeferred(RetryOperator.of(retry))
                    .doOnError(e -> {
                        if (retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt() > 0) {
                            retryCounter.increment();
                        }
                    });
        }

        // Apply circuit breaker (wraps retry)
        if (config.getCircuitBreaker().isEnabled()) {
            result = result.transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
        }

        return result;
    }

    private Retry createRetry(CefResilienceProperties.RetryConfig retryConfig) {
        if (!retryConfig.isEnabled()) {
            return Retry.ofDefaults(SERVICE_NAME + "-retry");
        }

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retryConfig.getMaxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        retryConfig.getWaitDuration().toMillis(),
                        retryConfig.getExponentialBackoffMultiplier(),
                        retryConfig.getMaxWaitDuration().toMillis()
                ))
                .retryOnException(e -> {
                    // Retry on transient errors, not on validation errors
                    if (e instanceof IllegalArgumentException) {
                        return false;
                    }
                    // Retry on connection, timeout, and most runtime exceptions
                    return true;
                })
                .build();

        Retry retry = RetryRegistry.of(config).retry(SERVICE_NAME + "-retry");

        // Log retry events
        retry.getEventPublisher()
                .onRetry(event -> log.warn("Retry attempt {} for embedding: {}", 
                        event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
                .onError(event -> log.error("Retry exhausted after {} attempts: {}", 
                        event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()))
                .onSuccess(event -> {
                    if (event.getNumberOfRetryAttempts() > 0) {
                        log.info("Embedding succeeded after {} retries", event.getNumberOfRetryAttempts());
                    }
                });

        return retry;
    }

    private CircuitBreaker createCircuitBreaker(CefResilienceProperties.CircuitBreakerConfig cbConfig) {
        if (!cbConfig.isEnabled()) {
            return CircuitBreaker.ofDefaults(SERVICE_NAME + "-cb");
        }

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbConfig.getFailureRateThreshold())
                .waitDurationInOpenState(cbConfig.getWaitDurationInOpenState())
                .slidingWindowSize(cbConfig.getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(cbConfig.getPermittedNumberOfCallsInHalfOpenState())
                .slowCallDurationThreshold(cbConfig.getSlowCallDurationThreshold())
                .slowCallRateThreshold(cbConfig.getSlowCallRateThreshold())
                .build();

        CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker(SERVICE_NAME + "-cb");

        // Log circuit breaker state transitions
        cb.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Circuit breaker state transition: {} -> {}", 
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        circuitBreakerOpenCounter.increment();
                    }
                })
                .onCallNotPermitted(event -> 
                    log.warn("Circuit breaker OPEN - call not permitted"))
                .onError(event -> 
                    log.debug("Circuit breaker recorded error: {}", event.getThrowable().getMessage()))
                .onSuccess(event -> 
                    log.debug("Circuit breaker recorded success in {}ms", event.getElapsedDuration().toMillis()));

        return cb;
    }

    /**
     * Get current circuit breaker state for health checks.
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Get retry metrics for monitoring.
     */
    public Retry.Metrics getRetryMetrics() {
        return retry.getMetrics();
    }

    /**
     * Get circuit breaker metrics for monitoring.
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }
}
