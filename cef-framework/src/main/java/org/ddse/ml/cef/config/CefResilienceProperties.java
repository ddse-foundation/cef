package org.ddse.ml.cef.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for CEF resilience patterns.
 * 
 * Configures retry, circuit breaker, timeout, and rate limiting for external
 * service calls (embedding, LLM, database).
 * 
 * <pre>
 * cef:
 *   resilience:
 *     enabled: true  # Master switch for resilience patterns
 *     embedding:
 *       retry:
 *         max-attempts: 3
 *         wait-duration: 1s
 *         exponential-backoff-multiplier: 2.0
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *         wait-duration-in-open-state: 30s
 *         sliding-window-size: 10
 *       timeout: 30s
 *     llm:
 *       retry:
 *         max-attempts: 2
 *         wait-duration: 2s
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *         wait-duration-in-open-state: 60s
 *       timeout: 120s
 *     database:
 *       timeout: 10s
 * </pre>
 *
 * @author mrmanna
 * @since 0.6
 */
@ConfigurationProperties(prefix = "cef.resilience")
@Validated
public class CefResilienceProperties {

    /**
     * Master switch to enable/disable all resilience patterns.
     * Default: false for development, true for production profile.
     */
    private boolean enabled = false;

    @Valid
    @NotNull
    private ServiceResilienceConfig embedding = new ServiceResilienceConfig();

    @Valid
    @NotNull
    private ServiceResilienceConfig llm = new ServiceResilienceConfig();

    @Valid
    @NotNull
    private DatabaseResilienceConfig database = new DatabaseResilienceConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ServiceResilienceConfig getEmbedding() {
        return embedding;
    }

    public void setEmbedding(ServiceResilienceConfig embedding) {
        this.embedding = embedding;
    }

    public ServiceResilienceConfig getLlm() {
        return llm;
    }

    public void setLlm(ServiceResilienceConfig llm) {
        this.llm = llm;
    }

    public DatabaseResilienceConfig getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseResilienceConfig database) {
        this.database = database;
    }

    /**
     * Resilience configuration for external services (embedding, LLM).
     */
    public static class ServiceResilienceConfig {

        @Valid
        @NotNull
        private RetryConfig retry = new RetryConfig();

        @Valid
        @NotNull
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        /**
         * Timeout for service calls. Default: 30s for embedding, 120s for LLM.
         */
        @NotNull
        private Duration timeout = Duration.ofSeconds(30);

        public RetryConfig getRetry() {
            return retry;
        }

        public void setRetry(RetryConfig retry) {
            this.retry = retry;
        }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * Retry configuration.
     */
    public static class RetryConfig {

        /**
         * Whether retry is enabled.
         */
        private boolean enabled = true;

        /**
         * Maximum number of retry attempts (including initial call).
         */
        @Min(1)
        @Max(10)
        private int maxAttempts = 3;

        /**
         * Initial wait duration between retries.
         */
        @NotNull
        private Duration waitDuration = Duration.ofSeconds(1);

        /**
         * Multiplier for exponential backoff. Set to 1.0 for fixed delay.
         */
        @Min(1)
        @Max(5)
        private double exponentialBackoffMultiplier = 2.0;

        /**
         * Maximum wait duration between retries (cap for exponential backoff).
         */
        @NotNull
        private Duration maxWaitDuration = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
        }

        public double getExponentialBackoffMultiplier() {
            return exponentialBackoffMultiplier;
        }

        public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) {
            this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
        }

        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }

        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }
    }

    /**
     * Circuit breaker configuration.
     */
    public static class CircuitBreakerConfig {

        /**
         * Whether circuit breaker is enabled.
         */
        private boolean enabled = true;

        /**
         * Failure rate threshold (percentage) to trip the circuit.
         */
        @Min(1)
        @Max(100)
        private int failureRateThreshold = 50;

        /**
         * Wait duration in open state before transitioning to half-open.
         */
        @NotNull
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);

        /**
         * Number of calls in sliding window for failure rate calculation.
         */
        @Min(5)
        @Max(100)
        private int slidingWindowSize = 10;

        /**
         * Number of permitted calls in half-open state.
         */
        @Min(1)
        @Max(20)
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /**
         * Slow call duration threshold. Calls slower than this are considered slow.
         */
        @NotNull
        private Duration slowCallDurationThreshold = Duration.ofSeconds(10);

        /**
         * Slow call rate threshold (percentage) to trip the circuit.
         */
        @Min(1)
        @Max(100)
        private int slowCallRateThreshold = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(int failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public int getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(int slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }
    }

    /**
     * Database resilience configuration.
     */
    public static class DatabaseResilienceConfig {

        /**
         * Timeout for database operations.
         */
        @NotNull
        private Duration timeout = Duration.ofSeconds(10);

        /**
         * Connection acquire timeout.
         */
        @NotNull
        private Duration connectionTimeout = Duration.ofSeconds(5);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
    }
}
