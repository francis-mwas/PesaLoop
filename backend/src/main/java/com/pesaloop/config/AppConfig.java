package com.pesaloop.config;

import com.pesaloop.group.application.port.out.SlugGenerator;
import com.pesaloop.shared.domain.TenantContext;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@ConfigurationPropertiesScan("com.pesaloop")
public class AppConfig {

    /**
     * JPA auditor — pulls the current user from TenantContext.
     * Populates created_by and updated_by fields automatically.
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            try {
                return Optional.of(TenantContext.getUserId());
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        };
    }

    /**
     * Shared WebClient for M-Pesa Daraja and SMS API calls.
     *
     * Configuration:
     *   - 10s connect timeout
     *   - 30s read/write timeout
     *   - Default Content-Type: application/json
     *   - Reactor Netty under the hood (non-blocking I/O)
     *
     * We call .block() in MpesaDarajaClient because the application is MVC
     * (not reactive end-to-end). This is intentional — M-Pesa calls are
     * low-volume and the blocking call is isolated to the payment adapter.
     */
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Slug generator — converts group name to URL-friendly slug.
     * "Wanjiku Welfare Group 2024" → "wanjiku-welfare-group-2024"
     */
    @Bean
    public SlugGenerator slugGenerator() {
        return groupName -> {
            if (groupName == null) return "group-" + UUID.randomUUID().toString().substring(0, 8);
            return groupName.toLowerCase()
                    .trim()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
        };
    }
}
