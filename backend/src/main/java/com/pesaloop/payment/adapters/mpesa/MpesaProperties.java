package com.pesaloop.payment.adapters.mpesa;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "pesaloop.mpesa")
public class MpesaProperties {
    private String consumerKey;
    private String consumerSecret;
    private String passkey;
    private String environment = "sandbox";
    private String baseUrl = "https://sandbox.safaricom.co.ke";
    private String callbackBaseUrl;

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }

    public String stkPushUrl() {
        return baseUrl + "/mpesa/stkpush/v1/processrequest";
    }

    public String b2cUrl() {
        return baseUrl + "/mpesa/b2c/v1/paymentrequest";
    }

    public String oauthUrl() {
        return baseUrl + "/oauth/v1/generate?grant_type=client_credentials";
    }

    public String c2bRegisterUrl() {
        return baseUrl + "/mpesa/c2b/v1/registerurl";
    }

    public String stkCallbackUrl() {
        return callbackBaseUrl + "/webhooks/mpesa/stk/callback";
    }

    public String b2cResultUrl() {
        return callbackBaseUrl + "/webhooks/mpesa/b2c/result";
    }

    public String c2bConfirmationUrl() {
        return callbackBaseUrl + "/webhooks/mpesa/c2b/confirmation";
    }
}
