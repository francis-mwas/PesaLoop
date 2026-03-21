package com.pesaloop.loan.application.port.out;

import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.shared.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — all loan account persistence.
 * Use cases and controllers depend only on this interface.
 * The JDBC adapter (LoanAccountJdbcAdapter) is the sole implementation.
 */
public interface LoanAccountRepository {

    // ── CRUD ──────────────────────────────────────────────────────────────────

    LoanAccount save(LoanAccount loan);
    Optional<LoanAccount> findById(UUID id);
    Optional<LoanAccount> findByGroupIdAndReference(UUID groupId, String reference);
    List<LoanAccount> findByMemberId(UUID memberId);
    List<LoanAccount> findByMemberIdAndStatus(UUID memberId, LoanStatus status);
    List<LoanAccount> findActiveByGroupId(UUID groupId);
    int countActiveByMemberIdAndProductId(UUID memberId, UUID productId);
    String nextLoanReference(UUID groupId, int year);

    // ── State-transition commands ─────────────────────────────────────────────

    /** Sets APPROVED status, locks principal, total interest, and due date. */
    void approve(UUID loanId, Money approvedPrincipal, Money totalInterest,
                 LocalDate dueDate, UUID approvedByUserId, String note);

    /** Sets REJECTED status, stores reason, releases any guarantors. */
    void reject(UUID loanId, String reason, UUID rejectedByUserId);

    /** Marks PENDING_DISBURSEMENT — a disbursement instruction has been issued. */
    void markPendingDisbursement(UUID loanId);

    /**
     * Activates the loan after the treasurer confirms disbursement.
     * Sets status=ACTIVE, principal_balance=principal_amount, records M-Pesa ref.
     */
    void activateAfterDisbursement(UUID loanId, String mpesaRef, UUID confirmedByUserId);

    // ── Rich queries ──────────────────────────────────────────────────────────

    /** Loan + member + product details joined in one query. No schedule. */
    Optional<LoanDetail> findDetailById(UUID loanId);

    /**
     * Loan + member + product details + full repayment schedule.
     * Used by GET /api/v1/loans/{loanId} — the full loan view.
     */
    Optional<LoanDetailWithSchedule> findDetailWithInstallments(UUID loanId);

    /**
     * Summary rows for the group loan book — joins members + products.
     * Filtered by the given statuses.
     */
    List<LoanSummaryRow> findLoanBook(UUID groupId, List<String> statuses);

    /** Member's full name (users JOIN members). */
    Optional<String> findMemberFullName(UUID memberId);

    // ── Aggregates ────────────────────────────────────────────────────────────

    /** Sum of (principal_balance + accrued_interest) for all ACTIVE loans in a group. */
    BigDecimal totalActiveLoanBook(UUID groupId);

    /** Sum of (principal_balance + accrued_interest) for all ACTIVE loans for one member. */
    BigDecimal totalActiveLoansForMember(UUID memberId);

    // ── Nested types ──────────────────────────────────────────────────────────

    record LoanDetail(
            UUID id, String loanReference,
            UUID memberId, String memberName, String memberNumber, String memberPhone,
            UUID productId, String productName,
            String status,
            Money principalAmount, Money totalInterestCharged,
            Money principalBalance, Money accruedInterest, Money penaltyBalance,
            Money totalPrincipalRepaid, Money totalInterestRepaid,
            LocalDate disbursementDate, LocalDate dueDate, String disbursementMpesaRef
    ) {}

    record LoanDetailWithSchedule(
            LoanDetail detail,
            List<InstallmentRow> schedule
    ) {}

    record InstallmentRow(
            UUID id, int installmentNumber, LocalDate dueDate,
            BigDecimal principalDue, BigDecimal interestDue,
            BigDecimal totalDue, BigDecimal principalPaid,
            BigDecimal interestPaid, BigDecimal penaltyPaid,
            String status, java.time.Instant paidAt
    ) {}

    record LoanSummaryRow(
            UUID loanId, String loanReference,
            String memberName, String memberNumber,
            String productName, String status,
            BigDecimal principalAmount, BigDecimal totalOutstanding,
            LocalDate dueDate, boolean overdue
    ) {}
}
