package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentRecordJdbcAdapter implements PaymentRecordRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void recordContributionPayment(
            UUID groupId, UUID entryId, UUID memberId,
            BigDecimal amount, String paymentMethod,
            String mpesaReference, String mpesaTransactionId,
            String phone, UUID recordedByUserId) {
        jdbc.update(
                """
                INSERT INTO payment_records
                    (id, group_id, entry_id, member_id, payment_type, amount, currency_code,
                     payment_method, mpesa_reference, mpesa_transaction_id, phone_number,
                     status, recorded_by, recorded_at)
                VALUES (gen_random_uuid(),?,?,?,'CONTRIBUTION',?,'KES',?,?,?,?,'COMPLETED',?,NOW())
                """,
                groupId, entryId, memberId, amount, paymentMethod,
                mpesaReference, mpesaTransactionId, phone, recordedByUserId);
    }

    @Override
    public void recordLoanRepayment(
            UUID groupId, UUID loanId, UUID memberId,
            BigDecimal amount, String paymentMethod,
            String mpesaReference, String narration, UUID recordedByUserId) {
        jdbc.update(
                """
                INSERT INTO payment_records
                    (id, group_id, loan_id, member_id, payment_type, amount, currency_code,
                     payment_method, mpesa_reference, mpesa_transaction_id, narration,
                     status, recorded_by, recorded_at)
                VALUES (gen_random_uuid(),?,?,?,'LOAN_REPAYMENT',?,'KES',?,?,?,?,'COMPLETED',?,NOW())
                """,
                groupId, loanId, memberId, amount, paymentMethod,
                mpesaReference, mpesaReference, narration, recordedByUserId);
    }

    @Override
    public void applyContributionPayment(UUID entryId, BigDecimal amount,
                                          String paymentMethod, String mpesaRef) {
        jdbc.update(
                """
                UPDATE contribution_entries
                   SET paid_amount          = paid_amount + ?,
                       last_payment_method  = ?,
                       last_mpesa_reference = ?,
                       first_payment_at     = COALESCE(first_payment_at, NOW()),
                       fully_paid_at = CASE WHEN paid_amount+? >= expected_amount THEN NOW() ELSE fully_paid_at END,
                       status = CASE WHEN paid_amount+? >= expected_amount THEN 'PAID' ELSE 'PARTIAL' END,
                       updated_at = NOW()
                 WHERE id = ?
                """,
                amount, paymentMethod, mpesaRef, amount, amount, entryId);
    }

    @Override
    public void incrementCycleCollected(UUID entryId, BigDecimal amount) {
        jdbc.update(
                "UPDATE contribution_cycles SET total_collected_amount=total_collected_amount+? WHERE id=(SELECT cycle_id FROM contribution_entries WHERE id=?)",
                amount, entryId);
    }

    @Override
    public void creditMemberSavings(UUID memberId, BigDecimal amount) {
        jdbc.update("UPDATE members SET savings_balance=savings_balance+? WHERE id=?", amount, memberId);
    }

    @Override
    public void updateLoanAfterRepayment(UUID loanId,
                                          BigDecimal principalBalance, BigDecimal accruedInterest,
                                          BigDecimal penaltyBalance, BigDecimal totalPrincipalRepaid,
                                          BigDecimal totalInterestRepaid, String newStatus) {
        jdbc.update(
                """
                UPDATE loan_accounts
                   SET principal_balance      = ?,
                       accrued_interest       = ?,
                       penalty_balance        = ?,
                       total_principal_repaid = ?,
                       total_interest_repaid  = ?,
                       status                 = ?,
                       settled_at = CASE WHEN ?='SETTLED' THEN NOW() ELSE settled_at END,
                       updated_at = NOW()
                 WHERE id = ?
                """,
                principalBalance, accruedInterest, penaltyBalance,
                totalPrincipalRepaid, totalInterestRepaid,
                newStatus, newStatus, loanId);
    }

    @Override
    public void applyRepaymentToInstallment(UUID loanId,
                                             BigDecimal principalApplied,
                                             BigDecimal interestApplied,
                                             BigDecimal penaltyApplied) {
        jdbc.update(
                """
                UPDATE repayment_installments
                   SET principal_paid = principal_paid + ?,
                       interest_paid  = interest_paid  + ?,
                       penalty_paid   = penalty_paid   + ?,
                       status = CASE
                           WHEN principal_paid+? >= principal_due AND interest_paid+? >= interest_due
                           THEN 'PAID' ELSE 'PARTIAL' END,
                       paid_at = CASE
                           WHEN principal_paid+? >= principal_due AND interest_paid+? >= interest_due
                           THEN NOW() ELSE paid_at END
                 WHERE loan_id = ?
                   AND installment_number = (
                       SELECT MIN(installment_number) FROM repayment_installments
                        WHERE loan_id=? AND status IN ('PENDING','PARTIAL','OVERDUE'))
                """,
                principalApplied, interestApplied, penaltyApplied,
                principalApplied, interestApplied,
                principalApplied, interestApplied,
                loanId, loanId);
    }

    @Override
    public void releaseGuarantors(UUID loanId) {
        jdbc.update("UPDATE loan_guarantors SET status='RELEASED' WHERE loan_id=?", loanId);
    }

    @Override
    public void saveUnmatchedPayment(String transactionId, BigDecimal amount,
                                      String phone, String billRefNumber, String shortCode) {
        jdbc.update(
                """
                INSERT INTO unmatched_payments
                    (id, mpesa_transaction_id, amount, phone_number,
                     bill_ref_number, business_short_code, resolved, received_at)
                VALUES (gen_random_uuid(),?,?,?,?,?,FALSE,NOW())
                ON CONFLICT (mpesa_transaction_id) DO NOTHING
                """,
                transactionId, amount, phone, billRefNumber, shortCode);
    }

    @Override
    public boolean alreadyProcessed(String mpesaTransactionId) {
        if (mpesaTransactionId == null) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_records WHERE mpesa_transaction_id=?",
                Integer.class, mpesaTransactionId);
        return count != null && count > 0;
    }

    @Override
    public void auditLog(UUID groupId, UUID actorId,
                          String entityType, UUID entityId,
                          String action, String jsonPayload) {
        jdbc.update(
                "INSERT INTO audit_log (group_id,actor_id,entity_type,entity_id,action,after_state) VALUES (?,?,?,?,?,?::jsonb)",
                groupId, actorId, entityType, entityId, action, jsonPayload);
    }

    @Override
    public void logNotification(UUID groupId, UUID memberId,
                                 String phone, String message, boolean sent) {
        jdbc.update(
                "INSERT INTO notification_log (id,group_id,member_id,channel,recipient,message,status,sent_at) VALUES (gen_random_uuid(),?,?,'SMS',?,?,?,NOW())",
                groupId, memberId, phone, message, sent ? "SENT" : "FAILED");
    }

    @Override
    public void confirmMgrPayout(UUID groupId, UUID recipientMemberId, String mpesaRef) {
        jdbc.update(
                """
                UPDATE contribution_cycles
                   SET mgr_paid_out_at = NOW(),
                       mgr_mpesa_ref   = ?
                 WHERE id = (
                     SELECT id FROM contribution_cycles
                      WHERE group_id = ? AND mgr_beneficiary_id = ?
                        AND mgr_paid_out_at IS NULL
                      ORDER BY cycle_number DESC LIMIT 1
                 )
                """,
                mpesaRef, groupId, recipientMemberId);
    }

    @Override
    public void confirmDividend(UUID groupId, UUID recipientMemberId, String mpesaRef) {
        jdbc.update(
                """
                UPDATE dividend_line_items
                   SET status   = 'PAID',
                       paid_at  = NOW(),
                       mpesa_ref = ?
                 WHERE member_id = ? AND status = 'PENDING'
                   AND distribution_id IN (
                       SELECT id FROM dividend_distributions WHERE group_id = ?
                   )
                """,
                mpesaRef, recipientMemberId, groupId);
    }

}
