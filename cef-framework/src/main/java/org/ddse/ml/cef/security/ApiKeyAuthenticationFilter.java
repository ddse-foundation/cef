package org.ddse.ml.cef.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Simple API-key authentication filter.
 * 
 * Validates a shared API key in a configurable header and establishes an authenticated
 * security context when the key matches. Returns 401 when the key is missing/invalid.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final CefSecurityProperties properties;

    public ApiKeyAuthenticationFilter(CefSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Allow health/info without auth for liveness probes
        String path = request.getRequestURI();
        if (path != null && (path.startsWith("/actuator/health") || path.startsWith("/actuator/info"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerName = properties.getApiKey().getHeader();
        String providedKey = request.getHeader(headerName);
        List<String> validKeys = properties.getApiKey().getKeys();

        if (providedKey == null || validKeys == null || validKeys.isEmpty() || !validKeys.contains(providedKey)) {
            log.debug("API key authentication failed for path {}", path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Invalid API key\"}");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "api-key-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
