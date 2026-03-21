package com.pesaloop.config;

import com.pesaloop.shared.adapters.web.SubscriptionAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers Spring MVC interceptors.
 *
 * SubscriptionAccessGuard runs on every /api/v1/** request and
 * returns 402 Payment Required for write operations when a group's
 * subscription is SUSPENDED or DORMANT.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SubscriptionAccessGuard subscriptionAccessGuard;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(subscriptionAccessGuard)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/payment-accounts/subscription",
                        "/actuator/**",
                        "/webhooks/**"
                );
    }
}
