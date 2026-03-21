package com.pesaloop.identity.adapters.security;

import com.pesaloop.shared.domain.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts JWT from Authorization header, validates it, and sets:
 *   1. Spring SecurityContext (for @PreAuthorize annotations)
 *   2. TenantContext (for repository queries scoped to group_id)
 *
 * TenantContext is ALWAYS cleared in the finally block — never leaks between requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (StringUtils.hasText(token)) {
                JwtTokenProvider.TokenClaims claims = jwtTokenProvider.validateAndExtract(token);

                if (claims.valid()) {
                    // Set Spring Security context
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.userId().toString(),
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Set tenant context (only if the token has a group — super admin tokens don't)
                    if (claims.groupId() != null) {
                        TenantContext.set(claims.groupId(), claims.userId(), claims.role());
                    }
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Critical: always clear to avoid context leaking across requests in thread pools
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        // Skip JWT filter for public endpoints
        return path.startsWith("/api/v1/auth/") ||
               path.startsWith("/webhooks/") ||
               path.startsWith("/actuator/health") ||
               path.equals("/api/v1/groups/check-slug");
    }
}
