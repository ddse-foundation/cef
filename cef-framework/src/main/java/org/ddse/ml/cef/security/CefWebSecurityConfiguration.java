package org.ddse.ml.cef.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.PrintWriter;

/**
 * HTTP security configuration for CEF.
 * Enables authentication when cef.security.enabled=true and configures the
 * chosen mode (API key, JWT/OAuth2 resource server, or Basic).
 */
@Configuration
@EnableConfigurationProperties(CefSecurityProperties.class)
@ConditionalOnProperty(prefix = "cef.security", name = "enabled", havingValue = "true")
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CefWebSecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CefWebSecurityConfiguration.class);

    @Bean
    public SecurityFilterChain cefSecurityFilterChain(HttpSecurity http,
                                                      CefSecurityProperties properties,
                                                      ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                                      AuthenticationEntryPoint authenticationEntryPoint) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint));

        switch (properties.getType()) {
            case API_KEY -> {
                log.info("CEF security: API-KEY mode enabled");
                http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
            }
            case JWT, OAUTH2 -> {
                log.warn("CEF security: JWT/OAuth2 requested but OAuth2 resource server dependency is not present. Falling back to HTTP Basic.");
                http.httpBasic(Customizer.withDefaults());
            }
            case BASIC -> {
                log.info("CEF security: HTTP Basic mode enabled");
                http.httpBasic(Customizer.withDefaults());
            }
            default -> {
                log.warn("CEF security: unknown type {}, defaulting to HTTP Basic", properties.getType());
                http.httpBasic(Customizer.withDefaults());
            }
        }

        return http.build();
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(CefSecurityProperties properties) {
        return new ApiKeyAuthenticationFilter(properties);
    }

    @Bean
    public AuthenticationEntryPoint cefAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            try (PrintWriter writer = response.getWriter()) {
                writer.write("{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}");
            }
        };
    }
}
