package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;

/**
 * Output port — what the application needs from M-Pesa.
 * Implemented by MpesaGatewayAdapter in adapters/mpesa.
 *
 * Param order matches MpesaDarajaClient.initiateStkPush exactly so
 * the adapter is a thin pass-through with no reordering.
 */
public interface MpesaGateway {

    /**
     * Initiates an STK Push (Lipa Na M-Pesa prompt on customer's phone).
     *
     * @param shortCode      Business short code (paybill/till)
     * @param phoneNumber    Customer phone in 254XXXXXXXXX format
     * @param amountKes      Amount in KES (whole number — Safaricom rounds)
     * @param accountRef     Account reference (shown on customer's phone)
     * @param checkoutId     Internal request ID for idempotency tracking
     */
    StkPushResult initiateStkPush(
            String shortCode,
            String phoneNumber,
            long amountKes,
            String accountRef,
            String checkoutId);

    void registerC2bUrls(String shortCode);

    record StkPushResult(
            boolean success,
            String checkoutRequestId,
            String merchantRequestId,
            String responseCode,
            String responseDescription,
            String customerMessage
    ) {
        public static StkPushResult success(String checkoutId, String merchantId, String msg) {
            return new StkPushResult(true, checkoutId, merchantId, "0", "Success", msg);
        }
        public static StkPushResult failure(String description) {
            return new StkPushResult(false, null, null, "1", description, null);
        }
        public boolean isSuccess() { return success; }
    }
}
