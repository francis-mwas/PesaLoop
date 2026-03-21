package com.pesaloop.payment.application.port.in;

import java.math.BigDecimal;

/** Input port — process a subscription payment to PesaLoop's own paybill. */
public interface ProcessBillingPaymentPort {
    boolean isPesaLoopPaybill(String shortCode);
    void process(String transactionId, BigDecimal amount, String phone,
                 String accountRef, String shortCode);
}
