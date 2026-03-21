package com.pesaloop.payment.adapters.mpesa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Safaricom Daraja API client — built with WebClient (non-blocking I/O).
 *
 * We call .block() to bridge back to the MVC world. This is safe because:
 *   - M-Pesa calls are low-frequency (not hot path)
 *   - Timeouts are enforced at the Reactor Netty level (AppConfig)
 *   - The blocking boundary is isolated to this adapter only
 *
 * Payment entry points supported:
 *   1. STK Push   — system prompts member's phone with PIN dialog
 *   2. C2B Paybill — member pays directly from their phone to the group's
 *                   paybill, entering their member number as account ref
 *   3. B2C        — system sends money to member (disbursements, payouts)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MpesaDarajaClient {

    private final MpesaProperties properties;
    private final WebClient webClient;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    private static final DateTimeFormatter MPESA_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ── 1. STK Push ───────────────────────────────────────────────────────────

    public StkPushResponse initiateStkPush(
            String shortcode, String phoneNumber, long amountKes,
            String accountRef, String checkoutId) {

        String timestamp = LocalDateTime.now().format(MPESA_TIMESTAMP);
        String password  = generateStkPassword(shortcode, timestamp);

        Map<String, Object> body = Map.ofEntries(
                Map.entry("BusinessShortCode", shortcode),
                Map.entry("Password",          password),
                Map.entry("Timestamp",         timestamp),
                Map.entry("TransactionType",   "CustomerPayBillOnline"),
                Map.entry("Amount",            amountKes),
                Map.entry("PartyA",            phoneNumber),
                Map.entry("PartyB",            shortcode),
                Map.entry("PhoneNumber",       phoneNumber),
                Map.entry("CallBackURL",       properties.stkCallbackUrl()),
                Map.entry("AccountReference",  accountRef),
                Map.entry("TransactionDesc",   "PesaLoop " + checkoutId)
        );

        return webClient.post()
                .uri(properties.stkPushUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(e -> Mono.error(new MpesaException("STK Push rejected: " + e))))
                .bodyToMono(StkPushResponse.class)
                .doOnSuccess(r -> log.info("STK Push sent: phone={} amount={} checkout={}",
                        phoneNumber, amountKes, r != null ? r.checkoutRequestId() : "null"))
                .doOnError(e -> log.error("STK Push failed: phone={} error={}", phoneNumber, e.getMessage()))
                .block();
    }

    // ── 2. C2B URL Registration ───────────────────────────────────────────────

    /**
     * Registers the group paybill with Safaricom so that when a member pays
     * directly via paybill (entering shortcode + account ref on their phone),
     * Safaricom forwards the event to our /webhooks/mpesa/c2b/confirmation endpoint.
     *
     * Call once per group when they configure their paybill integration.
     * Safe to re-call — Safaricom treats re-registration as an update.
     */
    public void registerC2bUrls(String shortcode) {
        Map<String, Object> body = Map.of(
                "ShortCode",       shortcode,
                "ResponseType",    "Completed",
                "ConfirmationURL", properties.c2bConfirmationUrl(),
                "ValidationURL",   properties.c2bConfirmationUrl()
        );

        webClient.post()
                .uri(properties.c2bRegisterUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(e -> Mono.error(new MpesaException("C2B registration failed: " + e))))
                .bodyToMono(String.class)
                .doOnSuccess(r -> log.info("C2B URLs registered for shortcode={}", shortcode))
                .doOnError(e -> log.error("C2B registration failed shortcode={}: {}", shortcode, e.getMessage()))
                .block();
    }

    // ── 3. B2C Disbursement ───────────────────────────────────────────────────

    public B2cResponse sendB2c(
            String shortcode, String recipientPhone, long amountKes,
            String occasion, String originatorId) {

        Map<String, Object> body = Map.ofEntries(
                Map.entry("InitiatorName",            "PesaLoopAPI"),
                Map.entry("SecurityCredential",       properties.getPasskey()),
                Map.entry("CommandID",                "BusinessPayment"),
                Map.entry("Amount",                   amountKes),
                Map.entry("PartyA",                   shortcode),
                Map.entry("PartyB",                   recipientPhone),
                Map.entry("Remarks",                  occasion),
                Map.entry("QueueTimeOutURL",          properties.b2cResultUrl()),
                Map.entry("ResultURL",                properties.b2cResultUrl()),
                Map.entry("Occassion",                occasion),
                Map.entry("OriginatorConversationID", originatorId)
        );

        return webClient.post()
                .uri(properties.b2cUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getAccessToken())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                        .flatMap(e -> Mono.error(new MpesaException("B2C rejected: " + e))))
                .bodyToMono(B2cResponse.class)
                .doOnSuccess(r -> log.info("B2C sent: phone={} amount={} originator={}",
                        recipientPhone, amountKes, originatorId))
                .doOnError(e -> log.error("B2C failed: phone={} error={}", recipientPhone, e.getMessage()))
                .block();
    }

    // ── OAuth token (cached, auto-refreshed 60s before expiry) ───────────────

    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        if (cached != null && !cached.isExpired()) return cached.token();

        String credentials = properties.getConsumerKey() + ":" + properties.getConsumerSecret();
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        OAuthResponse resp = webClient.get()
                .uri(properties.oauthUrl())
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class)
                        .flatMap(e -> Mono.error(new MpesaException("OAuth failed: " + e))))
                .bodyToMono(OAuthResponse.class)
                .block();

        if (resp == null || resp.accessToken() == null) {
            throw new MpesaException("Empty OAuth response from Daraja");
        }

        long ttl = resp.expiresIn() != null ? resp.expiresIn() : 3600L;
        CachedToken token = new CachedToken(resp.accessToken(), Instant.now().plusSeconds(ttl - 60));
        tokenCache.set(token);
        log.debug("M-Pesa OAuth token refreshed (expires in {}s)", ttl);
        return token.token();
    }

    private String generateStkPassword(String shortcode, String timestamp) {
        String raw = shortcode + properties.getPasskey() + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // ── Response / cache records ──────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StkPushResponse(
            @JsonProperty("MerchantRequestID")   String merchantRequestId,
            @JsonProperty("CheckoutRequestID")   String checkoutRequestId,
            @JsonProperty("ResponseCode")         String responseCode,
            @JsonProperty("ResponseDescription") String responseDescription,
            @JsonProperty("CustomerMessage")      String customerMessage
    ) {
        public boolean isSuccess() { return "0".equals(responseCode); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record B2cResponse(
            @JsonProperty("ConversationID")             String conversationId,
            @JsonProperty("OriginatorConversationID")   String originatorConversationId,
            @JsonProperty("ResponseCode")               String responseCode,
            @JsonProperty("ResponseDescription")        String responseDescription
    ) {
        public boolean isAccepted() { return "0".equals(responseCode); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OAuthResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")   Long expiresIn
    ) {}

    private record CachedToken(String token, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    public static class MpesaException extends RuntimeException {
        public MpesaException(String msg)                  { super(msg); }
        public MpesaException(String msg, Throwable cause) { super(msg, cause); }
    }
}
