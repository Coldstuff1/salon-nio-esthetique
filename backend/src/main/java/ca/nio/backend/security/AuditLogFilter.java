package ca.nio.backend.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs every change a signed-in user makes through the API (who, what, outcome), so admin and staff actions such
 * as cancellations or catalog edits can be traced afterwards. Runs inside the security filters, so the caller is
 * known.
 */
@Slf4j
@Component
class AuditLogFilter extends OncePerRequestFilter {

    private static final Set<String> READ_ONLY = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return READ_ONLY.contains(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken token) {
                log.info("{} {} -> {} by {} ({})", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        token.getToken().getSubject(), token.getName());
            }
        }
    }

}
