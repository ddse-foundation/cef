package org.ddse.ml.cef.security;

import org.junit.jupiter.api.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SecurityAuditLogger.
 *
 * @author mrmanna
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Security Audit Logger Tests")
class SecurityAuditLoggerTest {

    private SecurityAuditLogger auditLogger;
    private CefSecurityProperties properties;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger auditLog;

    @BeforeEach
    void setUp() {
        properties = new CefSecurityProperties();
        properties.getAudit().setEnabled(true);
        properties.getAudit().setLogSuccess(true);
        properties.getAudit().setLogFailure(true);
        properties.getAudit().setLogDataAccess(true);
        auditLogger = new SecurityAuditLogger(properties);

        // Set up log capture
        auditLog = (Logger) LoggerFactory.getLogger("security.audit");
        listAppender = new ListAppender<>();
        listAppender.start();
        auditLog.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        auditLog.detachAppender(listAppender);
    }

    // ==================== Authentication Logging ====================

    @Test
    @Order(1)
    @DisplayName("Should log successful authentication")
    void shouldLogAuthenticationSuccess() {
        auditLogger.logAuthenticationSuccess("user@example.com", "JWT", "192.168.1.1");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("AUTHENTICATION_SUCCESS") &&
                event.getFormattedMessage().contains("user@example.com") &&
                event.getFormattedMessage().contains("JWT") &&
                event.getLevel() == Level.INFO
        );
    }

    @Test
    @Order(2)
    @DisplayName("Should log failed authentication")
    void shouldLogAuthenticationFailure() {
        auditLogger.logAuthenticationFailure("hacker@bad.com", "API_KEY", "10.0.0.1", "Invalid API key");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("AUTHENTICATION_FAILURE") &&
                event.getFormattedMessage().contains("hacker@bad.com") &&
                event.getFormattedMessage().contains("Invalid API key") &&
                event.getLevel() == Level.WARN
        );
    }

    @Test
    @Order(3)
    @DisplayName("Should not log success when disabled")
    void shouldNotLogSuccessWhenDisabled() {
        properties.getAudit().setLogSuccess(false);
        
        auditLogger.logAuthenticationSuccess("user@example.com", "JWT", "192.168.1.1");

        assertThat(listAppender.list).noneMatch(event ->
                event.getFormattedMessage().contains("AUTHENTICATION_SUCCESS")
        );
    }

    // ==================== Authorization Logging ====================

    @Test
    @Order(4)
    @DisplayName("Should log authorization granted")
    void shouldLogAuthorizationGranted() {
        auditLogger.logAuthorizationDecision("admin@example.com", "/api/nodes", "READ", true, null);

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("AUTHORIZATION_GRANTED") &&
                event.getFormattedMessage().contains("admin@example.com") &&
                event.getFormattedMessage().contains("/api/nodes") &&
                event.getLevel() == Level.INFO
        );
    }

    @Test
    @Order(5)
    @DisplayName("Should log authorization denied")
    void shouldLogAuthorizationDenied() {
        auditLogger.logAuthorizationDecision("user@example.com", "/api/admin", "DELETE", false, "Insufficient permissions");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("AUTHORIZATION_DENIED") &&
                event.getFormattedMessage().contains("/api/admin") &&
                event.getFormattedMessage().contains("Insufficient permissions") &&
                event.getLevel() == Level.WARN
        );
    }

    // ==================== Data Access Logging ====================

    @Test
    @Order(6)
    @DisplayName("Should log data access events")
    void shouldLogDataAccess() {
        UUID entityId = UUID.randomUUID();
        auditLogger.logDataAccess("user@example.com", "READ", "Node", entityId, "Patient record");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("DATA_ACCESS") &&
                event.getFormattedMessage().contains("READ") &&
                event.getFormattedMessage().contains("Node") &&
                event.getFormattedMessage().contains(entityId.toString()) &&
                event.getLevel() == Level.INFO
        );
    }

    @Test
    @Order(7)
    @DisplayName("Should not log data access when disabled")
    void shouldNotLogDataAccessWhenDisabled() {
        properties.getAudit().setLogDataAccess(false);
        
        auditLogger.logDataAccess("user@example.com", "READ", "Node", UUID.randomUUID(), "test");

        assertThat(listAppender.list).noneMatch(event ->
                event.getFormattedMessage().contains("DATA_ACCESS")
        );
    }

    // ==================== Security Violation Logging ====================

    @Test
    @Order(8)
    @DisplayName("Should always log security violations")
    void shouldAlwaysLogSecurityViolations() {
        // Even when audit is disabled, security violations should be logged
        properties.getAudit().setEnabled(false);
        
        auditLogger.logSecurityViolation("attacker", "SQL_INJECTION", "10.0.0.1", "Malicious payload detected");

        // Security violations bypass enabled check
        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("SECURITY_VIOLATION") &&
                event.getFormattedMessage().contains("SQL_INJECTION") &&
                event.getLevel() == Level.ERROR
        );
    }

    // ==================== Injection Attempt Logging ====================

    @Test
    @Order(9)
    @DisplayName("Should log injection attempts")
    void shouldLogInjectionAttempts() {
        auditLogger.logInjectionAttempt("unknown", "SQL_INJECTION", "192.168.1.100", 
                "'; DROP TABLE users; --");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("INJECTION_ATTEMPT") &&
                event.getFormattedMessage().contains("SQL_INJECTION") &&
                event.getLevel() == Level.ERROR
        );
    }

    @Test
    @Order(10)
    @DisplayName("Should truncate long payloads in injection logs")
    void shouldTruncateLongPayloads() {
        String longPayload = "SELECT ".repeat(100);
        auditLogger.logInjectionAttempt("unknown", "SQL_INJECTION", "192.168.1.100", longPayload);

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("INJECTION_ATTEMPT") &&
                event.getFormattedMessage().length() < longPayload.length() + 200 // reasonable limit
        );
    }

    // ==================== Rate Limit Logging ====================

    @Test
    @Order(11)
    @DisplayName("Should log rate limit violations")
    void shouldLogRateLimitViolations() {
        auditLogger.logRateLimitViolation("user@example.com", "/api/search", "192.168.1.1", 100);

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("RATE_LIMIT_EXCEEDED") &&
                event.getFormattedMessage().contains("/api/search") &&
                event.getFormattedMessage().contains("100") &&
                event.getLevel() == Level.WARN
        );
    }

    // ==================== Configuration Change Logging ====================

    @Test
    @Order(12)
    @DisplayName("Should log configuration changes")
    void shouldLogConfigurationChanges() {
        auditLogger.logConfigurationChange("admin", "cef.graph.store", "neo4j", "pg-sql");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("CONFIGURATION_CHANGE") &&
                event.getFormattedMessage().contains("cef.graph.store") &&
                event.getLevel() == Level.INFO
        );
    }

    @Test
    @Order(13)
    @DisplayName("Should mask sensitive configuration values")
    void shouldMaskSensitiveValues() {
        auditLogger.logConfigurationChange("admin", "cef.security.api-key.secret", "old-secret", "new-secret");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("CONFIGURATION_CHANGE") &&
                event.getFormattedMessage().contains("********") &&
                !event.getFormattedMessage().contains("old-secret") &&
                !event.getFormattedMessage().contains("new-secret")
        );
    }

    // ==================== Input Sanitization ====================

    @Test
    @Order(14)
    @DisplayName("Should sanitize principal names")
    void shouldSanitizePrincipalNames() {
        auditLogger.logAuthenticationSuccess("user\nwith\rnewlines", "JWT", "192.168.1.1");

        // Should not contain raw newlines in log
        assertThat(listAppender.list).noneMatch(event ->
                event.getFormattedMessage().contains("\n") || event.getFormattedMessage().contains("\r")
        );
    }

    @Test
    @Order(15)
    @DisplayName("Should handle anonymous principals")
    void shouldHandleAnonymousPrincipals() {
        auditLogger.logAuthenticationFailure(null, "NONE", "192.168.1.1", "No credentials");

        assertThat(listAppender.list).anyMatch(event ->
                event.getFormattedMessage().contains("anonymous")
        );
    }

    // ==================== Audit Disabled ====================

    @Test
    @Order(16)
    @DisplayName("Should not log when audit is disabled (except security violations)")
    void shouldNotLogWhenAuditDisabled() {
        properties.getAudit().setEnabled(false);
        
        auditLogger.logAuthenticationSuccess("user", "JWT", "192.168.1.1");
        auditLogger.logAuthenticationFailure("user", "JWT", "192.168.1.1", "test");
        auditLogger.logDataAccess("user", "READ", "Node", UUID.randomUUID(), "test");

        assertThat(listAppender.list).noneMatch(event ->
                event.getFormattedMessage().contains("AUTHENTICATION_SUCCESS") ||
                event.getFormattedMessage().contains("AUTHENTICATION_FAILURE") ||
                event.getFormattedMessage().contains("DATA_ACCESS")
        );
    }
}
