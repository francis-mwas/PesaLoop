package com.pesaloop.payment.application.port.in;

import java.math.BigDecimal;

/** Input port — process an inbound C2B paybill payment from M-Pesa webhook. */
public interface ProcessC2bPaymentPort {

    /**
     * @param transactionId   M-Pesa TransID (unique, idempotency key)
     * @param amount          payment amount (already parsed from webhook)
     * @param phone           payer's MSISDN in 254XXXXXXXXX format
     * @param billRefNumber   account reference entered by member (e.g. "M-017")
     * @param shortCode       business short code that received the payment
     * @param firstName       payer's first name from M-Pesa (may be null)
     * @param lastName        payer's last name from M-Pesa (may be null)
     */
    void process(String transactionId, BigDecimal amount, String phone,
                 String billRefNumber, String shortCode,
                 String firstName, String lastName);
}
