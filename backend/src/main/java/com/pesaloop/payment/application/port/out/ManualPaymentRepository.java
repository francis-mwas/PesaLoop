package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Output port — persistence operations for manual payment recording.
 * RecordManualPaymentUseCase depends on this, not on JdbcTemplate.
 */
public interface ManualPaymentRepository {

    /** Validates member belongs to group and is active. Returns member info. */
    MemberInfo findActiveMember(UUID memberId, UUID groupId);

    /** Finds the current open cycle entry for this member. */
    UUID findCurrentOpenCycleId(UUID groupId, UUID memberId);

    /** Validates cycle is open. Throws if not. Returns cycle number. */
    int validateAndGetCycleNumber(UUID cycleId, UUID groupId);

    /** Applies a contribution payment to the entry, cycle totals, and member balance. */
    void applyContributionPayment(UUID cycleId, UUID memberId, BigDecimal amount,
                                   String methodName, String reference, UUID recordedBy);

    /** Validates loan is active. Returns loan info. */
    LoanInfo findActiveLoan(UUID loanId, UUID memberId, UUID groupId);

    /** Applies loan repayment — penalty, interest, principal in order. */
    void applyLoanRepayment(UUID loanId, BigDecimal toPrincipal, BigDecimal toInterest,
                             BigDecimal toPenalty, boolean fullySettled);

    /** Updates the next due installment. */
    void applyRepaymentToInstallment(UUID loanId, BigDecimal toPrincipal,
                                      BigDecimal toInterest, BigDecimal toPenalty);

    /** Releases guarantors on settlement. */
    void releaseGuarantors(UUID loanId);

    /** Records the payment_records row and audit_log entry. */
    UUID recordPayment(UUID groupId, UUID memberId, UUID cycleId, UUID loanId,
                        String paymentType, BigDecimal amount, String methodName,
                        String reference, String notes, UUID recordedBy);

    record MemberInfo(UUID id, String memberNumber, String status, String fullName) {}
    record LoanInfo(UUID id, String loanRef, String status,
                    BigDecimal principalBalance, BigDecimal accruedInterest, BigDecimal penaltyBalance) {}
}
