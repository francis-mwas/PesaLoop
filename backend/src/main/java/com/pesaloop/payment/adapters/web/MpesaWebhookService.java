package com.pesaloop.payment.adapters.web;

import com.pesaloop.payment.application.port.out.StkPushProcessingRepository;
import com.pesaloop.payment.application.port.out.StkPushProcessingRepository.StkRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Processes confirmed M-Pesa STK Push and B2C webhook events.
 * All persistence delegated to StkPushProcessingRepository output port.
 * No SQL in this class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaWebhookService {

    private final StkPushProcessingRepository stkProcessingRepository;

    // ── STK Push ─────────────────────────────────────────────────────────────

    @Transactional
    public void processStkSuccess(String merchantRequestId, String checkoutRequestId,
                                   BigDecimal amount, String mpesaReceiptNumber, String phone) {

        if (stkProcessingRepository.paymentAlreadyProcessed(mpesaReceiptNumber)) {
            log.info("STK already processed: mpesaRef={} — skipping", mpesaReceiptNumber);
            return;
        }

        StkRecord pending = stkProcessingRepository.findPendingStkRecord(checkoutRequestId)
                .orElse(null);
        if (pending == null) {
            log.warn("No pending STK record for checkoutRequestId={}", checkoutRequestId);
            return;
        }

        stkProcessingRepository.processStkSuccess(
                pending.id(), pending.entryId(), pending.memberId(), pending.groupId(),
                amount, mpesaReceiptNumber, checkoutRequestId);

        log.info("STK success: checkout={} amount={} receipt={}",
                checkoutRequestId, amount, mpesaReceiptNumber);
    }

    @Transactional
    public void processStkFailure(String merchantRequestId, String resultDesc) {
        stkProcessingRepository.markStkFailed(merchantRequestId, resultDesc);
        log.info("STK failed: merchantId={} reason={}", merchantRequestId, resultDesc);
    }

    // ── B2C (disbursements / payouts) ─────────────────────────────────────────

    /**
     * Called when a B2C disbursement initiated by PesaLoop reaches the member successfully.
     * Updates the disbursement_instruction record to COMPLETED.
     */
    @Transactional
    public void processB2cSuccess(String conversationId, String originatorConversationId,
                                   BigDecimal amount, String receiptNumber) {
        stkProcessingRepository.markB2cSuccess(conversationId, originatorConversationId,
                amount, receiptNumber);
    }

    /**
     * Called when a B2C disbursement fails (e.g. invalid phone, insufficient float).
     * Updates the disbursement_instruction record to FAILED.
     */
    @Transactional
    public void processB2cFailure(String conversationId, String resultDesc) {
        stkProcessingRepository.markB2cFailed(conversationId, resultDesc);
    }
}
