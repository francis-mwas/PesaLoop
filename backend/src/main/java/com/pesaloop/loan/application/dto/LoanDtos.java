package com.pesaloop.loan.application.dto;

import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LoanDtos {
    private LoanDtos() {}

    // ── Requests ──────────────────────────────────────────────────────────────

    /**
     * Member submits a loan application.
     * The system automatically checks eligibility against the product's rules.
     */
    public record ApplyForLoanRequest(
            @NotNull UUID productId,
            @NotNull @Positive BigDecimal amount,
            /** Optional: member can specify desired repayment periods (must be ≤ product max). */
            Integer repaymentPeriods,
            String applicationNote,
            /** Phone to disburse to. Defaults to member's registered phone. */
            String disbursementPhone,
            /**
             * Optional: member nominates their own guarantors at application time.
             * If product.requiresGuarantor=true and this list is non-empty,
             * guarantors are created immediately and notified by SMS.
             * Admin can also assign guarantors later via POST /loans/{id}/guarantors.
             */
            java.util.List<UUID> guarantorMemberIds
    ) {}

    /**
     * Admin/treasurer approves or rejects a loan application.
     */
    public record ProcessLoanApplicationRequest(
            @NotNull UUID loanId,
            @NotNull LoanDecision decision,
            String note,
            /** Admin can override the disbursement amount (downward only). */
            BigDecimal approvedAmount
    ) {
        public enum LoanDecision { APPROVE, REJECT }
    }

    /**
     * Used by ApproveLoanPort / ApproveLoanUseCase to approve or reject a loan.
     * Extends ProcessLoanApplicationRequest with optional overrides (amount, periods).
     */
    public record ProcessLoanRequest(
            @NotNull UUID loanId,
            @NotNull LoanDecision decision,
            String note,
            /** Admin may reduce amount but never increase it. Null = use requested amount. */
            Double approvedAmount,
            /** Admin may override repayment periods. Null = use product default. */
            Integer approvedPeriods
    ) {
        public enum LoanDecision { APPROVE, REJECT }
    }


    /**
     * Triggers the actual disbursement of an approved loan via M-Pesa B2C.
     * Separate from approval so treasurer can batch-approve then disburse later.
     */
    public record DisburseLoanRequest(
            @NotNull UUID loanId,
            /** Override phone for disbursement if different from member's registered phone. */
            String disbursementPhone
    ) {}

    /**
     * Records a loan repayment (manual cash or bank).
     * STK Push repayments flow through MpesaWebhookService.
     */
    public record RecordRepaymentRequest(
            @NotNull UUID loanId,
            @NotNull @Positive BigDecimal amount,
            @NotNull String paymentMethod,
            String mpesaReference,
            String notes
    ) {}

    // ── Responses ─────────────────────────────────────────────────────────────

    public record LoanApplicationResponse(
            UUID loanId,
            String loanReference,
            UUID memberId,
            String memberName,
            String memberNumber,
            UUID productId,
            String productName,
            BigDecimal requestedAmount,
            BigDecimal approvedAmount,
            LoanStatus status,
            // Eligibility result (only on initial application)
            Boolean eligible,
            String ineligibilityReason,
            // Guarantor status
            List<GuarantorStatus> guarantors,
            Instant createdAt
    ) {}

    public record GuarantorStatus(
            UUID guarantorMemberId,
            String guarantorName,
            String status    // PENDING | ACCEPTED | DECLINED
    ) {}

    public record LoanDetailResponse(
            UUID loanId,
            String loanReference,
            UUID memberId,
            String memberName,
            String memberNumber,
            String productName,
            LoanStatus status,
            BigDecimal principalAmount,
            BigDecimal totalInterestCharged,
            BigDecimal principalBalance,
            BigDecimal accruedInterest,
            BigDecimal penaltyBalance,
            BigDecimal totalOutstanding,
            BigDecimal totalPrincipalRepaid,
            BigDecimal totalInterestRepaid,
            LocalDate disbursementDate,
            LocalDate dueDate,
            String disbursementMpesaRef,
            List<InstallmentResponse> schedule
    ) {}

    public record InstallmentResponse(
            int installmentNumber,
            LocalDate dueDate,
            BigDecimal principalDue,
            BigDecimal interestDue,
            BigDecimal totalDue,
            BigDecimal balanceAfter,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal penaltyPaid,
            RepaymentInstallment.InstallmentStatus status,
            Instant paidAt
    ) {}

    public record DisburseResponse(
            UUID loanId,
            String loanReference,
            BigDecimal amount,
            String phone,
            String mpesaConversationId,
            LoanStatus status
    ) {}

    public record RepaymentResponse(
            UUID loanId,
            String loanReference,
            BigDecimal paymentAmount,
            BigDecimal appliedToPenalty,
            BigDecimal appliedToInterest,
            BigDecimal appliedToPrincipal,
            BigDecimal remainingOutstanding,
            LoanStatus loanStatus
    ) {}

    /** Summary row used in the group's loan book view */
    public record LoanSummaryResponse(
            UUID loanId,
            String loanReference,
            String memberName,
            String memberNumber,
            String productName,
            LoanStatus status,
            BigDecimal principalAmount,
            BigDecimal totalOutstanding,
            LocalDate dueDate,
            boolean overdue
    ) {}
}
