package com.pesaloop.payment.adapters.mpesa;

import com.pesaloop.payment.application.port.out.MpesaGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter — implements MpesaGateway using Safaricom Daraja API.
 * This is the only class that knows about MpesaDarajaClient.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MpesaGatewayAdapter implements MpesaGateway {

    private final MpesaDarajaClient darajaClient;

    @Override
    public StkPushResult initiateStkPush(
            String shortCode, String phoneNumber, long amountKes,
            String accountRef, String checkoutId) {
        try {
            MpesaDarajaClient.StkPushResponse resp =
                    darajaClient.initiateStkPush(shortCode, phoneNumber, amountKes, accountRef, checkoutId);
            return new StkPushResult(
                    true,
                    resp.checkoutRequestId(),
                    resp.merchantRequestId(),
                    resp.responseCode(),
                    resp.responseDescription(),
                    resp.customerMessage()
            );
        } catch (Exception e) {
            log.error("STK Push failed: phone={} amount={} error={}", phoneNumber, amountKes, e.getMessage());
            return StkPushResult.failure(e.getMessage());
        }
    }

    @Override
    public void registerC2bUrls(String shortCode) {
        darajaClient.registerC2bUrls(shortCode);
    }
}
