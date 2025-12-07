package org.ddse.ml.cef.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for CEF security features.
 * 
 * <p>Automatically configures:</p>
 * <ul>
 *   <li>Security properties binding</li>
 *   <li>Input sanitizer</li>
 *   <li>Audit logger</li>
 *   <li>Exception handler</li>
 * </ul>
 * 
 * <p>Security features are enabled by default but can be disabled:</p>
 * <pre>
 * cef:
 *   security:
 *     enabled: false
 * </pre>
 *
 * @author mrmanna
 * @since v0.6
 */
@Configuration
@EnableConfigurationProperties(CefSecurityProperties.class)
public class CefSecurityAutoConfiguration {

    /**
     * Input sanitizer for preventing injection attacks.
     */
    @Bean
    public InputSanitizer inputSanitizer(CefSecurityProperties properties) {
        return new InputSanitizer(properties);
    }

    /**
     * Security audit logger for logging security events.
     */
    @Bean
    public SecurityAuditLogger securityAuditLogger(CefSecurityProperties properties) {
        return new SecurityAuditLogger(properties);
    }

    /**
     * Exception handler for sanitizing error responses.
     */
    @Bean
    public CefExceptionHandler cefExceptionHandler(SecurityAuditLogger auditLogger) {
        return new CefExceptionHandler(auditLogger);
    }
}
