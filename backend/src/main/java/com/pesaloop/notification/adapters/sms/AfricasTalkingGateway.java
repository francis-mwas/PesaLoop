package com.pesaloop.notification.adapters.sms;

import com.pesaloop.notification.application.port.out.SmsGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Secondary adapter — implements SmsGateway using Africa's Talking.
 * This is the only class that knows about the AT HTTP API.
 * SmsService only sees SmsGateway (the port).
 */
@Slf4j
@Component
public class AfricasTalkingGateway implements SmsGateway {

    private final WebClient webClient;
    private final String apiKey;
    private final String username;
    private final String senderId;
    private final boolean sandboxMode;

    private static final String SANDBOX_URL    = "https://api.sandbox.africastalking.com/version1/messaging";
    private static final String PRODUCTION_URL = "https://api.africastalking.com/version1/messaging";

    public AfricasTalkingGateway(
            WebClient webClient,
            @Value("${pesaloop.sms.api-key:}") String apiKey,
            @Value("${pesaloop.sms.username:sandbox}") String username,
            @Value("${pesaloop.sms.sender-id:PesaLoop}") String senderId,
            @Value("${pesaloop.mpesa.environment:sandbox}") String environment) {
        this.webClient   = webClient;
        this.apiKey      = apiKey;
        this.username    = username;
        this.senderId    = senderId;
        this.sandboxMode = "sandbox".equalsIgnoreCase(environment);
    }

    @Override
    public boolean send(String phone, String message) {
        if (!StringUtils.hasText(apiKey)) {
            log.info("SMS skipped (no API key configured — expected in dev). To: {}", phone);
            return false;
        }

        String url      = sandboxMode ? SANDBOX_URL : PRODUCTION_URL;
        String formatted = phone.startsWith("+") ? phone : "+" + phone;

        try {
            String response = webClient.post()
                    .uri(url)
                    .header("apiKey", apiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("username", username)
                            .with("to", formatted)
                            .with("message", message)
                            .with("from", sandboxMode ? "" : senderId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            boolean ok = response != null && response.contains("\"status\":\"Success\"");
            if (ok) log.info("SMS sent to {}", phone);
            else    log.warn("SMS rejected: phone={} response={}", phone, response);
            return ok;

        } catch (Exception e) {
            log.error("SMS failed: phone={} error={}", phone, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendBulk(List<String> phones, String message) {
        if (phones == null || phones.isEmpty()) return false;
        String joined = String.join(",",
                phones.stream().map(p -> p.startsWith("+") ? p : "+" + p).toList());
        return send(joined, message);
    }
}