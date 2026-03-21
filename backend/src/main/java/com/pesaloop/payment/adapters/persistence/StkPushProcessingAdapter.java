package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.StkPushProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — idempotent STK Push payment recording. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class StkPushProcessingAdapter implements StkPushProcessingRepository {

    private final JdbcTemplate jdbc;

    @Override
    public boolean paymentAlreadyProcessed(String mpesaReceiptNumber) {
        if (mpesaReceiptNumber == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_records WHERE mpesa_transaction_id=?",
                Integer.class, mpesaReceiptNumber);
        return count != null && count > 0;
    }

    @Override
    public Optional<StkRecord> findPendingStkRecord(String checkoutRequestId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,group_id,member_id,entry_id FROM stk_push_requests WHERE checkout_request_id=? AND status='PENDING'",
                    (rs, row) -> new StkRecord(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("group_id")),
                            UUID.fromString(rs.getString("member_id")),
                            rs.getString("entry_id") != null
                                    ? UUID.fromString(rs.getString("entry_id")) : null),
                    checkoutRequestId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    @Transactional
    public void processStkSuccess(UUID stkId, UUID entryId, UUID memberId, UUID groupId,
                                   BigDecimal amount, String mpesaReceiptNumber,
                                   String checkoutRequestId) {
        if (entryId != null) {
            jdbc.update(
                    """
                    UPDATE contribution_entries
                       SET paid_amount=paid_amount+?, last_payment_method='MPESA_STK_PUSH',
                           last_mpesa_reference=?,
                           first_payment_at=COALESCE(first_payment_at,NOW()),
                           fully_paid_at=CASE WHEN paid_amount+?>=expected_amount THEN NOW() ELSE fully_paid_at END,
                           status=CASE WHEN paid_amount+?>=expected_amount THEN 'PAID' ELSE 'PARTIAL' END,
                           updated_at=NOW()
                     WHERE id=?
                    """,
                    amount, mpesaReceiptNumber, amount, amount, entryId);

            jdbc.update(
                    "UPDATE contribution_cycles SET total_collected_amount=total_collected_amount+? WHERE id=(SELECT cycle_id FROM contribution_entries WHERE id=?)",
                    amount, entryId);
        }

        jdbc.update("UPDATE members SET savings_balance=savings_balance+? WHERE id=?", amount, memberId);

        jdbc.update(
                """
                INSERT INTO payment_records
                    (id,group_id,entry_id,member_id,payment_type,amount,currency_code,
                     payment_method,mpesa_reference,mpesa_transaction_id,phone_number,status,recorded_at)
                VALUES (gen_random_uuid(),?,?,?,'CONTRIBUTION',?,'KES','MPESA_STK_PUSH',?,?,NULL,'COMPLETED',NOW())
                """,
                groupId, entryId, memberId, amount, mpesaReceiptNumber, mpesaReceiptNumber);

        jdbc.update("UPDATE stk_push_requests SET status='COMPLETED',mpesa_receipt_number=? WHERE checkout_request_id=?",
                mpesaReceiptNumber, checkoutRequestId);
    }

    @Override
    public void markStkFailed(String merchantRequestId, String failureReason) {
        jdbc.update("UPDATE stk_push_requests SET status='FAILED',failure_reason=? WHERE merchant_request_id=?",
                failureReason, merchantRequestId);
    }

    @Override
    public void markB2cSuccess(String conversationId, String originatorConversationId,
                                java.math.BigDecimal amount, String receiptNumber) {
        jdbc.update(
                """
                UPDATE disbursement_instructions
                   SET status = 'COMPLETED',
                       external_mpesa_ref = ?,
                       confirmation_notes = ?,
                       confirmed_at = NOW(),
                       updated_at = NOW()
                 WHERE conversation_id = ?
                   AND status = 'PENDING_MPESA'
                """,
                receiptNumber,
                "B2C completed. Receipt: " + receiptNumber + " Amount: " + amount,
                conversationId);
        log.info("B2C success: conversationId={} receipt={} amount={}",
                conversationId, receiptNumber, amount);
    }

    @Override
    public void markB2cFailed(String conversationId, String resultDesc) {
        jdbc.update(
                """
                UPDATE disbursement_instructions
                   SET status = 'FAILED',
                       confirmation_notes = ?,
                       updated_at = NOW()
                 WHERE conversation_id = ?
                   AND status = 'PENDING_MPESA'
                """,
                resultDesc, conversationId);
        log.warn("B2C failed: conversationId={} reason={}", conversationId, resultDesc);
    }

}
