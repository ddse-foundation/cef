package org.ddse.ml.cef.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Global exception handler that sanitizes error responses.
 * 
 * <p>Ensures that internal implementation details are never exposed to clients.
 * All exceptions are logged with a unique error ID that can be used for debugging.</p>
 *
 * @author mrmanna
 * @since v0.6
 */
@ControllerAdvice
public class CefExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CefExceptionHandler.class);

    private final SecurityAuditLogger auditLogger;

    public CefExceptionHandler(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Handle validation exceptions.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(
            MethodArgumentNotValidException e,
            ServerWebExchange exchange) {
        
        String errorId = generateErrorId();
        String clientMessage = "Validation failed. Please check your request parameters.";
        
        log.warn("Validation failed [errorId={}]: {}", errorId, e.getMessage());
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        errorId,
                        clientMessage,
                        HttpStatus.BAD_REQUEST.value(),
                        Instant.now()
                )));
    }

    /**
     * Handle security exceptions.
     */
    @ExceptionHandler(SecurityException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleSecurityException(
            SecurityException e,
            ServerWebExchange exchange) {
        
        String errorId = generateErrorId();
        String remoteAddress = getRemoteAddress(exchange);
        
        log.error("Security exception [errorId={}] from {}: {}", 
                errorId, remoteAddress, e.getMessage());
        
        auditLogger.logSecurityViolation(
                "unknown",
                "SECURITY_EXCEPTION",
                remoteAddress,
                e.getMessage()
        );
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        errorId,
                        "Access denied.",
                        HttpStatus.FORBIDDEN.value(),
                        Instant.now()
                )));
    }

    /**
     * Handle illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException e,
            ServerWebExchange exchange) {
        
        String errorId = generateErrorId();
        
        log.warn("Invalid argument [errorId={}]: {}", errorId, e.getMessage());
        
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        errorId,
                        "Invalid request. Please check your parameters.",
                        HttpStatus.BAD_REQUEST.value(),
                        Instant.now()
                )));
    }

    /**
     * Handle all other exceptions.
     * Never expose internal details - just return a generic error with reference ID.
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleException(
            Exception e,
            ServerWebExchange exchange) {
        
        String errorId = generateErrorId();
        
        // Log full details for debugging
        log.error("Unhandled exception [errorId={}]", errorId, e);
        
        // Return sanitized response - never expose stack traces or internal details
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(
                        errorId,
                        "An internal error occurred. Reference: " + errorId,
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        Instant.now()
                )));
    }

    private String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getRemoteAddress(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "unknown";
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * Standardized error response.
     */
    public record ErrorResponse(
            String errorId,
            String message,
            int status,
            Instant timestamp
    ) {}
}
