package com.pesaloop.shared.adapters.web;

import com.pesaloop.shared.domain.SubscriptionInfo;
import com.pesaloop.shared.domain.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Intercepts every API write request and enforces subscription-based access control.
 *
 * States:
 *   TRIAL, ACTIVE, GRACE → full access
 *   SUSPENDED, DORMANT   → writes blocked (HTTP 402)
 *   CANCELLED            → writes blocked during export window, then HTTP 410 GONE
 *
 * Exempt paths (always pass through):
 *   /api/v1/auth/**                          — login must always work
 *   /webhooks/**                             — M-Pesa callbacks must always arrive
 *   /actuator/**                             — health checks
 *   /api/v1/payment-accounts/subscription   — must always see billing status
 *   /api/v1/payment-accounts/*\/register-c2b — admin must always be able to fix M-Pesa setup
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionAccessGuard implements HandlerInterceptor {

    private final JdbcTemplate jdbc;
    private final SubscriptionResponseWriter responseWriter;

    private static final String[] EXEMPT_PREFIXES = {
            "/api/v1/auth/",
            "/webhooks/",
            "/actuator/",
            "/api/v1/payment-accounts/subscription",
    };

    private static final String REGISTER_C2B_SUFFIX = "/register-c2b";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String method = request.getMethod();
        String path   = request.getRequestURI();

        if (isReadMethod(method)) return true;
        if (isExemptPath(path))   return true;

        UUID groupId = TenantContext.getGroupIdOrNull();
        if (groupId == null) return true;

        SubscriptionInfo sub = loadSubscription(groupId);
        if (sub == null) return true; // fail-open — avoids locking out new groups

        return switch (sub.status()) {
            case "TRIAL", "ACTIVE", "GRACE" -> true;

            case "SUSPENDED", "DORMANT" -> {
                log.info("Write blocked — subscription {}: group={} path={}", sub.status(), groupId, path);
                responseWriter.writeBlocked(response, sub, "SUSPENDED");
                yield false;
            }

            case "CANCELLED" -> {
                int exportWindowDays = getConfig("post_cancel_export_window_days", 30);
                boolean withinExportWindow = sub.cancelledAt() != null
                        && LocalDate.now().isBefore(sub.cancelledAt().plusDays(exportWindowDays));

                if (withinExportWindow) {
                    log.info("Write blocked — CANCELLED within export window: group={}", groupId);
                    responseWriter.writeBlocked(response, sub, "CANCELLED");
                } else {
                    log.info("Request blocked — CANCELLED + export window expired: group={}", groupId);
                    responseWriter.writeGone(response, sub);
                }
                yield false;
            }

            default -> true;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isReadMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private boolean isExemptPath(String path) {
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return path.endsWith(REGISTER_C2B_SUFFIX);
    }

    private SubscriptionInfo loadSubscription(UUID groupId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT gs.status, gs.plan_code, g.slug,
                           gs.cancelled_at::DATE AS cancelled_date
                      FROM group_subscriptions gs
                      JOIN groups g ON g.id = gs.group_id
                     WHERE gs.group_id = ?
                    """,
                    (rs, row) -> new SubscriptionInfo(
                            rs.getString("status"),
                            rs.getString("plan_code"),
                            rs.getString("slug"),
                            rs.getObject("cancelled_date", LocalDate.class)
                    ),
                    groupId
            );
        } catch (Exception e) {
            log.warn("Could not load subscription for group={}: {}", groupId, e.getMessage());
            return null;
        }
    }

    private int getConfig(String key, int defaultValue) {
        try {
            Integer val = jdbc.queryForObject(
                    "SELECT config_value::INT FROM platform_config WHERE config_key=? AND group_id IS NULL",
                    Integer.class, key);
            return val != null ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
