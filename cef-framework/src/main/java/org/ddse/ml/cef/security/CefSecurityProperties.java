package org.ddse.ml.cef.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for CEF security features.
 * 
 * <p>Security is disabled by default for development but can be enabled via configuration.
 * Production deployments should always enable security.</p>
 * 
 * Example configuration:
 * <pre>
 * cef:
 *   security:
 *     enabled: true
 *     type: api-key
 *     api-key:
 *       header: X-API-Key
 *       keys:
 *         - ${CEF_API_KEY_1}
 * </pre>
 *
 * @author mrmanna
 * @since v0.6
 */
@ConfigurationProperties(prefix = "cef.security")
@Validated
public class CefSecurityProperties {

    /**
     * Enable or disable security. Default: false (disabled for development).
     * Production deployments should set this to true.
     */
    private boolean enabled = false;

    /**
     * Security type: jwt, api-key, or oauth2.
     */
    private SecurityType type = SecurityType.API_KEY;

    /**
     * JWT configuration for OAuth2/OIDC authentication.
     */
    private JwtConfig jwt = new JwtConfig();

    /**
     * API Key configuration for simple API key authentication.
     */
    private ApiKeyConfig apiKey = new ApiKeyConfig();

    /**
     * Input sanitization settings.
     */
    private SanitizationConfig sanitization = new SanitizationConfig();

    /**
     * Audit logging settings.
     */
    private AuditConfig audit = new AuditConfig();

    public enum SecurityType {
        JWT,
        API_KEY,
        OAUTH2,
        BASIC
    }

    public static class JwtConfig {
        /**
         * JWT issuer URI for token validation.
         */
        private String issuerUri;

        /**
         * Expected audience in JWT claims.
         */
        private String audience;

        /**
         * JWKS URI for key discovery. If not set, derived from issuer-uri.
         */
        private String jwkSetUri;

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }
    }

    public static class ApiKeyConfig {
        /**
         * HTTP header name for API key. Default: X-API-Key.
         */
        private String header = "X-API-Key";

        /**
         * List of valid API keys. Use environment variables in production.
         */
        private List<String> keys = new ArrayList<>();

        public String getHeader() {
            return header;
        }

        public void setHeader(String header) {
            this.header = header;
        }

        public List<String> getKeys() {
            return keys;
        }

        public void setKeys(List<String> keys) {
            this.keys = keys;
        }
    }

    public static class SanitizationConfig {
        /**
         * Enable input sanitization to prevent injection attacks.
         */
        private boolean enabled = true;

        /**
         * Maximum length for user-provided text inputs.
         */
        private int maxInputLength = 100000;

        /**
         * Maximum length for node labels.
         */
        private int maxLabelLength = 100;

        /**
         * Maximum length for relation type names.
         */
        private int maxRelationTypeLength = 100;

        /**
         * Enable HTML stripping from inputs.
         */
        private boolean stripHtml = true;

        /**
         * Enable SQL injection pattern detection.
         */
        private boolean detectSqlInjection = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxInputLength() {
            return maxInputLength;
        }

        public void setMaxInputLength(int maxInputLength) {
            this.maxInputLength = maxInputLength;
        }

        public int getMaxLabelLength() {
            return maxLabelLength;
        }

        public void setMaxLabelLength(int maxLabelLength) {
            this.maxLabelLength = maxLabelLength;
        }

        public int getMaxRelationTypeLength() {
            return maxRelationTypeLength;
        }

        public void setMaxRelationTypeLength(int maxRelationTypeLength) {
            this.maxRelationTypeLength = maxRelationTypeLength;
        }

        public boolean isStripHtml() {
            return stripHtml;
        }

        public void setStripHtml(boolean stripHtml) {
            this.stripHtml = stripHtml;
        }

        public boolean isDetectSqlInjection() {
            return detectSqlInjection;
        }

        public void setDetectSqlInjection(boolean detectSqlInjection) {
            this.detectSqlInjection = detectSqlInjection;
        }
    }

    public static class AuditConfig {
        /**
         * Enable audit logging for security events.
         */
        private boolean enabled = true;

        /**
         * Log successful authentication attempts.
         */
        private boolean logSuccess = false;

        /**
         * Log failed authentication attempts.
         */
        private boolean logFailure = true;

        /**
         * Log data access events.
         */
        private boolean logDataAccess = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogSuccess() {
            return logSuccess;
        }

        public void setLogSuccess(boolean logSuccess) {
            this.logSuccess = logSuccess;
        }

        public boolean isLogFailure() {
            return logFailure;
        }

        public void setLogFailure(boolean logFailure) {
            this.logFailure = logFailure;
        }

        public boolean isLogDataAccess() {
            return logDataAccess;
        }

        public void setLogDataAccess(boolean logDataAccess) {
            this.logDataAccess = logDataAccess;
        }
    }

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SecurityType getType() {
        return type;
    }

    public void setType(SecurityType type) {
        this.type = type;
    }

    public JwtConfig getJwt() {
        return jwt;
    }

    public void setJwt(JwtConfig jwt) {
        this.jwt = jwt;
    }

    public ApiKeyConfig getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKeyConfig apiKey) {
        this.apiKey = apiKey;
    }

    public SanitizationConfig getSanitization() {
        return sanitization;
    }

    public void setSanitization(SanitizationConfig sanitization) {
        this.sanitization = sanitization;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }
}
