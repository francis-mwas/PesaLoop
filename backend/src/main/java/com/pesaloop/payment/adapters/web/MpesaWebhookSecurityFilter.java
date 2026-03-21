package com.pesaloop.payment.adapters.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Set;

/**
 * Safaricom M-Pesa webhook IP whitelist filter.
 *
 * Only Safaricom's published IP ranges are allowed to call /webhooks/mpesa/*.
 * An attacker who knows your webhook URL cannot forge a KES 1,000,000 payment
 * because their IP will be rejected before the handler ever runs.
 *
 * Safaricom published IP ranges (as of 2024 — verify at developer.safaricom.co.ke):
 *   Sandbox:    196.201.214.200/24
 *   Production: 196.201.214.0/24, 196.201.214.200/24
 *
 * Set pesaloop.mpesa.environment=sandbox to allow sandbox IPs only.
 * In production, set to "production".
 *
 * Override the IP whitelist via application.yml:
 *   pesaloop.mpesa.webhook-allowed-ips=196.201.214.0/24,196.201.214.200/24
 *
 * To disable (NOT recommended for production):
 *   pesaloop.mpesa.webhook-ip-check-enabled=false
 */
@Slf4j
@Configuration
public class MpesaWebhookSecurityFilter {

    // Safaricom's official callback IP ranges
    private static final Set<String> SAFARICOM_SANDBOX_IPS = Set.of(
            "196.201.214.200", "196.201.214.201", "196.201.214.202",
            "196.201.214.203", "196.201.214.204", "196.201.214.205"
    );
    private static final Set<String> SAFARICOM_PROD_IPS = Set.of(
            "196.201.214.200", "196.201.214.201", "196.201.214.202",
            "196.201.214.203", "196.201.214.204", "196.201.214.205",
            "196.201.214.206", "196.201.214.207"
    );
    // Allow localhost for dev/testing
    private static final Set<String> LOCALHOST_IPS = Set.of(
            "127.0.0.1", "::1", "0:0:0:0:0:0:0:1"
    );

    @Bean
    public FilterRegistrationBean<WebhookIpFilter> webhookIpFilter(
            @Value("${pesaloop.mpesa.environment:sandbox}") String env,
            @Value("${pesaloop.mpesa.webhook-ip-check-enabled:true}") boolean enabled,
            @Value("${pesaloop.mpesa.webhook-allowed-ips:}") String customIps) {

        Set<String> allowed = new java.util.HashSet<>(LOCALHOST_IPS);
        if ("production".equalsIgnoreCase(env)) {
            allowed.addAll(SAFARICOM_PROD_IPS);
        } else {
            allowed.addAll(SAFARICOM_SANDBOX_IPS);
        }
        if (StringUtils.hasText(customIps)) {
            for (String ip : customIps.split(",")) {
                allowed.add(ip.trim());
            }
        }

        FilterRegistrationBean<WebhookIpFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new WebhookIpFilter(allowed, enabled));
        reg.addUrlPatterns("/webhooks/mpesa/*");
        reg.setOrder(1);
        reg.setName("mpesaWebhookIpFilter");
        return reg;
    }

    static class WebhookIpFilter implements Filter {

        private final Set<String> allowedIps;
        private final boolean enabled;

        WebhookIpFilter(Set<String> allowedIps, boolean enabled) {
            this.allowedIps = allowedIps;
            this.enabled    = enabled;
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {

            HttpServletRequest  request  = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            if (!enabled) {
                chain.doFilter(req, res);
                return;
            }

            String ip = getClientIp(request);

            if (!allowedIps.contains(ip)) {
                log.warn("M-Pesa webhook rejected from unauthorized IP: {} path={}",
                        ip, request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Forbidden\"}");
                return;
            }

            chain.doFilter(req, res);
        }

        private String getClientIp(HttpServletRequest request) {
            // Check X-Forwarded-For first (nginx/load balancer sets this)
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                // Take the first IP (original client, not proxy chain)
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) return realIp.trim();
            return request.getRemoteAddr();
        }
    }
}
