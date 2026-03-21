package com.pesaloop.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates critical configuration on startup.
 * Logs clear warnings for missing config, fails hard in production
 * if security-critical values are still at their insecure defaults.
 */
@Slf4j
@Component
public class StartupConfigValidator {

    @Value("${pesaloop.jwt.secret:change-me-in-production-use-at-least-64-chars-for-hs512-algorithm}")
    private String jwtSecret;

    @Value("${pesaloop.mpesa.consumer-key:}")
    private String mpesaConsumerKey;

    @Value("${pesaloop.mpesa.consumer-secret:}")
    private String mpesaConsumerSecret;

    @Value("${pesaloop.mpesa.passkey:}")
    private String mpesaPasskey;

    @Value("${pesaloop.sms.api-key:}")
    private String smsApiKey;

    @Value("${pesaloop.mpesa.environment:sandbox}")
    private String mpesaEnvironment;

    @Value("${pesaloop.mpesa.callback-base-url:}")
    private String callbackBaseUrl;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final String DEFAULT_JWT_SECRET =
            "change-me-in-production-use-at-least-64-chars-for-hs512-algorithm";
    private static final String DEV_JWT_SECRET =
            "dev-secret-key-minimum-64-chars-for-hs512-do-not-use-in-prod-replace-me";

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfig() {
        boolean isProduction = "production".equalsIgnoreCase(mpesaEnvironment)
                || activeProfile.contains("prod");

        log.info("=== PesaLoop Config Validation (environment: {}) ===", mpesaEnvironment);

        // ── JWT Secret ────────────────────────────────────────────────────────
        if (DEFAULT_JWT_SECRET.equals(jwtSecret) || DEV_JWT_SECRET.equals(jwtSecret)) {
            if (isProduction) {
                throw new IllegalStateException(
                        "FATAL: JWT secret is set to the default dev value in a production environment. " +
                        "Set pesaloop.jwt.secret to a random 64+ character string. " +
                        "Generate one with: openssl rand -base64 64");
            } else {
                log.warn("JWT secret is the default dev value — DO NOT use in production");
            }
        } else if (jwtSecret.length() < 64) {
            if (isProduction) {
                throw new IllegalStateException(
                        "FATAL: JWT secret is too short (" + jwtSecret.length() + " chars). " +
                        "Minimum 64 characters required for HS512.");
            } else {
                log.warn("JWT secret is only {} chars — minimum 64 recommended", jwtSecret.length());
            }
        } else {
            log.info("JWT secret: OK ({} chars)", jwtSecret.length());
        }

        // ── M-Pesa credentials ────────────────────────────────────────────────
        if (mpesaConsumerKey.isBlank() || mpesaConsumerSecret.isBlank()) {
            log.warn("M-Pesa credentials not configured — STK Push and C2B URL registration will fail. " +
                     "Set pesaloop.mpesa.consumer-key and pesaloop.mpesa.consumer-secret");
        } else {
            log.info("M-Pesa credentials: OK");
        }

        if (mpesaPasskey.isBlank()) {
            log.warn("M-Pesa passkey not configured — STK Push will fail. " +
                     "Set pesaloop.mpesa.passkey");
        }

        // ── Callback URL ──────────────────────────────────────────────────────
        if (callbackBaseUrl.isBlank()) {
            log.warn("M-Pesa callback-base-url not set — Safaricom cannot reach webhooks. " +
                     "Set pesaloop.mpesa.callback-base-url to your public HTTPS URL. " +
                     "For local testing: use ngrok (ngrok http 8080)");
        } else if (!callbackBaseUrl.startsWith("https://")) {
            log.warn("M-Pesa callback-base-url should be HTTPS — Safaricom rejects plain HTTP. " +
                     "Current value: {}", callbackBaseUrl);
        } else {
            log.info("M-Pesa callback URL: {}", callbackBaseUrl);
        }

        // ── SMS ───────────────────────────────────────────────────────────────
        if (smsApiKey.isBlank()) {
            log.warn("Africa's Talking API key not configured — OTPs and notifications will not be sent. " +
                     "Set pesaloop.sms.api-key (get from https://africastalking.com)");
        } else {
            log.info("SMS (Africa's Talking): OK");
        }

        log.info("=== Config validation complete ===");
    }
}
