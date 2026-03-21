package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Output port — STK Push result processing (idempotent payment recording). */
public interface StkPushProcessingRepository {

    boolean paymentAlreadyProcessed(String mpesaReceiptNumber);

    Optional<StkRecord> findPendingStkRecord(String checkoutRequestId);

    void processStkSuccess(UUID stkId, UUID entryId, UUID memberId, UUID groupId,
                            BigDecimal amount, String mpesaReceiptNumber,
                            String checkoutRequestId);

    void markStkFailed(String merchantRequestId, String failureReason);

    record StkRecord(UUID id, UUID groupId, UUID memberId, UUID entryId) {}

    /** Mark a B2C disbursement as successfully completed. */
    void markB2cSuccess(String conversationId, String originatorConversationId,
                         java.math.BigDecimal amount, String receiptNumber);

    /** Mark a B2C disbursement as failed. */
    void markB2cFailed(String conversationId, String resultDesc);

}
