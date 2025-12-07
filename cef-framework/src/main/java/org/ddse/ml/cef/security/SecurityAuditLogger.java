package org.ddse.ml.cef.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Security audit logger for CEF operations.
 * 
 * <p>Provides structured audit logging for:
 * <ul>
 *   <li>Authentication events (success/failure)</li>
 *   <li>Authorization decisions</li>
 *   <li>Data access events</li>
 *   <li>Security violations</li>
 * </ul>
 *
 * @author mrmanna
 * @since v0.6
 */
@Component
public class SecurityAuditLogger {

    // Use a dedicated security audit logger
    private static final Logger auditLog = LoggerFactory.getLogger("security.audit");
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    private final CefSecurityProperties securityProperties;

    public SecurityAuditLogger(CefSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * Log successful authentication event.
     */
    public void logAuthenticationSuccess(String principal, String method, String remoteAddress) {
        if (!isAuditEnabled() || !securityProperties.getAudit().isLogSuccess()) {
            return;
        }

        auditLog.info("AUTHENTICATION_SUCCESS principal={} method={} remoteAddress={} timestamp={}",
                sanitizePrincipal(principal),
                method,
                sanitizeIpAddress(remoteAddress),
                Instant.now());
    }

    /**
     * Log failed authentication attempt.
     */
    public void logAuthenticationFailure(String principal, String method, String remoteAddress, String reason) {
        if (!isAuditEnabled() || !securityProperties.getAudit().isLogFailure()) {
            return;
        }

        auditLog.warn("AUTHENTICATION_FAILURE principal={} method={} remoteAddress={} reason={} timestamp={}",
                sanitizePrincipal(principal),
                method,
                sanitizeIpAddress(remoteAddress),
                sanitizeReason(reason),
                Instant.now());
    }

    /**
     * Log authorization decision.
     */
    public void logAuthorizationDecision(String principal, String resource, String action, 
                                         boolean allowed, String reason) {
        if (!isAuditEnabled()) {
            return;
        }

        if (allowed && !securityProperties.getAudit().isLogSuccess()) {
            return;
        }

        String level = allowed ? "info" : "warn";
        String event = allowed ? "AUTHORIZATION_GRANTED" : "AUTHORIZATION_DENIED";

        if (allowed) {
            auditLog.info("{} principal={} resource={} action={} timestamp={}",
                    event,
                    sanitizePrincipal(principal),
                    resource,
                    action,
                    Instant.now());
        } else {
            auditLog.warn("{} principal={} resource={} action={} reason={} timestamp={}",
                    event,
                    sanitizePrincipal(principal),
                    resource,
                    action,
                    sanitizeReason(reason),
                    Instant.now());
        }
    }

    /**
     * Log data access event.
     */
    public void logDataAccess(String principal, String operation, String entityType, 
                             UUID entityId, String details) {
        if (!isAuditEnabled() || !securityProperties.getAudit().isLogDataAccess()) {
            return;
        }

        auditLog.info("DATA_ACCESS principal={} operation={} entityType={} entityId={} details={} timestamp={}",
                sanitizePrincipal(principal),
                operation,
                entityType,
                entityId,
                sanitizeDetails(details),
                Instant.now());
    }

    /**
     * Log security violation event.
     */
    public void logSecurityViolation(String principal, String violationType, 
                                     String remoteAddress, String details) {
        // Security violations are always logged regardless of configuration
        auditLog.error("SECURITY_VIOLATION principal={} type={} remoteAddress={} details={} timestamp={}",
                sanitizePrincipal(principal),
                violationType,
                sanitizeIpAddress(remoteAddress),
                sanitizeDetails(details),
                Instant.now());
    }

    /**
     * Log injection attack attempt.
     */
    public void logInjectionAttempt(String principal, String injectionType, 
                                    String remoteAddress, String payload) {
        // Injection attempts are always logged
        auditLog.error("INJECTION_ATTEMPT principal={} type={} remoteAddress={} payload={} timestamp={}",
                sanitizePrincipal(principal),
                injectionType,
                sanitizeIpAddress(remoteAddress),
                truncatePayload(payload),
                Instant.now());
    }

    /**
     * Log rate limit violation.
     */
    public void logRateLimitViolation(String principal, String endpoint, 
                                      String remoteAddress, int limit) {
        auditLog.warn("RATE_LIMIT_EXCEEDED principal={} endpoint={} remoteAddress={} limit={} timestamp={}",
                sanitizePrincipal(principal),
                endpoint,
                sanitizeIpAddress(remoteAddress),
                limit,
                Instant.now());
    }

    /**
     * Log configuration change.
     */
    public void logConfigurationChange(String principal, String configKey, 
                                       String oldValue, String newValue) {
        auditLog.info("CONFIGURATION_CHANGE principal={} key={} oldValue={} newValue={} timestamp={}",
                sanitizePrincipal(principal),
                configKey,
                maskSensitiveValue(configKey, oldValue),
                maskSensitiveValue(configKey, newValue),
                Instant.now());
    }

    private boolean isAuditEnabled() {
        return securityProperties.getAudit().isEnabled();
    }

    private String sanitizePrincipal(String principal) {
        if (principal == null) {
            return "anonymous";
        }
        // Remove any control characters and limit length
        String sanitized = principal.replaceAll("[\\p{Cntrl}]", "");
        return sanitized.substring(0, Math.min(sanitized.length(), 100));
    }

    private String sanitizeIpAddress(String ip) {
        if (ip == null) {
            return "unknown";
        }
        // Basic IPv4/IPv6 validation
        if (ip.matches("^[0-9a-fA-F.:]+$")) {
            return ip;
        }
        return "invalid";
    }

    private String sanitizeReason(String reason) {
        if (reason == null) {
            return "unspecified";
        }
        // Remove newlines and limit length
        return reason.replaceAll("[\\r\\n]", " ").substring(0, Math.min(reason.length(), 200));
    }

    private String sanitizeDetails(String details) {
        if (details == null) {
            return "";
        }
        // Remove newlines and limit length
        return details.replaceAll("[\\r\\n]", " ").substring(0, Math.min(details.length(), 500));
    }

    private String truncatePayload(String payload) {
        if (payload == null) {
            return "";
        }
        // Truncate and encode for safe logging
        String truncated = payload.substring(0, Math.min(payload.length(), 200));
        return truncated.replaceAll("[\\r\\n]", "\\\\n")
                       .replaceAll("[\\p{Cntrl}]", "?");
    }

    private String maskSensitiveValue(String key, String value) {
        if (value == null) {
            return null;
        }
        // Mask values for sensitive configuration keys
        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("password") || 
            lowerKey.contains("secret") || 
            lowerKey.contains("key") ||
            lowerKey.contains("token") ||
            lowerKey.contains("credential")) {
            return "********";
        }
        return value;
    }
}
