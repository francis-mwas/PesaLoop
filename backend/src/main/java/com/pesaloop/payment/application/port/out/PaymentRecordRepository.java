package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Output port — payment record and audit log persistence.
 * All use cases that record payments depend on this port.
 * The adapter owns the SQL.
 */
public interface PaymentRecordRepository {

    /**
     * Record a contribution payment (cash, bank, STK, C2B).
     * Idempotency: mpesaTransactionId is unique in payment_records.
     */
    void recordContributionPayment(
            UUID groupId, UUID entryId, UUID memberId,
            BigDecimal amount, String paymentMethod,
            String mpesaReference, String mpesaTransactionId,
            String phone, UUID recordedByUserId);

    /**
     * Record a loan repayment.
     */
    void recordLoanRepayment(
            UUID groupId, UUID loanId, UUID memberId,
            BigDecimal amount, String paymentMethod,
            String mpesaReference, String narration,
            UUID recordedByUserId);

    /**
     * Update contribution entry paid amount and status.
     */
    void applyContributionPayment(UUID entryId, BigDecimal amount,
                                   String paymentMethod, String mpesaRef);

    /**
     * Update contribution cycle running total.
     */
    void incrementCycleCollected(UUID entryId, BigDecimal amount);

    /**
     * Credit member savings balance.
     */
    void creditMemberSavings(UUID memberId, BigDecimal amount);

    /**
     * Update loan account balances after repayment.
     */
    void updateLoanAfterRepayment(UUID loanId,
                                   BigDecimal principalBalance,
                                   BigDecimal accruedInterest,
                                   BigDecimal penaltyBalance,
                                   BigDecimal totalPrincipalRepaid,
                                   BigDecimal totalInterestRepaid,
                                   String newStatus);

    /**
     * Update the next due installment after a repayment.
     */
    void applyRepaymentToInstallment(UUID loanId,
                                      BigDecimal principalApplied,
                                      BigDecimal interestApplied,
                                      BigDecimal penaltyApplied);

    /**
     * Release guarantors when loan is settled.
     */
    void releaseGuarantors(UUID loanId);

    /**
     * Save unmatched C2B payment for admin review.
     */
    void saveUnmatchedPayment(String transactionId, BigDecimal amount,
                               String phone, String billRefNumber,
                               String shortCode);

    /**
     * Check idempotency — has this M-Pesa transaction been processed?
     */
    boolean alreadyProcessed(String mpesaTransactionId);

    /**
     * Write an audit log entry.
     */
    void auditLog(UUID groupId, UUID actorId,
                  String entityType, UUID entityId,
                  String action, String jsonPayload);

    /**
     * Log a notification to notification_log.
     */
    void logNotification(UUID groupId, UUID memberId,
                          String phone, String message, boolean sent);

    /** Confirm MGR payout cycle. */
    void confirmMgrPayout(UUID groupId, UUID recipientMemberId, String mpesaRef);

    /** Confirm dividend line item. */
    void confirmDividend(UUID groupId, UUID recipientMemberId, String mpesaRef);

}
