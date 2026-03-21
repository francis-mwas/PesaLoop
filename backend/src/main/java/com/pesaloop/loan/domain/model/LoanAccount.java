package com.pesaloop.loan.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LoanAccount is the aggregate root for a single loan taken by a member.
 *
 * Lifecycle:
 *   APPLICATION_SUBMITTED → PENDING_GUARANTOR → PENDING_APPROVAL
 *   → APPROVED → DISBURSED → ACTIVE → SETTLED | DEFAULTED | WRITTEN_OFF
 *
 * Financial tracking:
 *   principalBalance      = original principal - total principal repaid
 *   accruedInterest       = interest calculated but not yet repaid
 *   totalInterestRepaid   = interest payments received to date
 *   totalPrincipalRepaid  = principal payments received to date
 *
 * From the chamaa spreadsheet we observed:
 *   - Interest column stays fixed across months → FLAT_RATE model (charged at disbursement)
 *   - Repayment column only appears once for Salome → bullet/lump-sum common
 *   - Some members have loans > their monthly savings → no strict savings cap for all groups
 */
@Getter
@Builder(toBuilder = true)
public class LoanAccount {

    private final UUID id;
    private final UUID groupId;
    private final UUID memberId;
    private final UUID productId;

    /**
     * Human-readable loan reference (e.g. "LN-2025-0001").
     * Generated once at application time and never changes.
     * Shown on receipts, SMS notifications, and admin screens.
     */
    private final String loanReference;

    private LoanStatus status;

    // Original terms (immutable after disbursement)
    private final Money principalAmount;
    private final Money totalInterestCharged;    // for FLAT_RATE: known at disbursement
    private final LocalDate disbursementDate;
    private final LocalDate dueDate;

    // Running balances
    private Money principalBalance;              // outstanding principal
    private Money accruedInterest;               // interest accrued but not yet paid
    private Money totalInterestRepaid;
    private Money totalPrincipalRepaid;
    private Money penaltyBalance;                // unpaid penalties

    // Guarantors
    @Builder.Default
    private List<UUID> guarantorMemberIds = new ArrayList<>();

    // Disbursement
    private String disbursementMpesaRef;
    private Instant disbursedAt;
    private UUID disbursedBy;

    // Settlement
    private Instant settledAt;
    private String settlementNote;

    // ── Derived properties ────────────────────────────────────────────────

    public Money totalOutstanding() {
        return principalBalance
                .add(accruedInterest)
                .add(penaltyBalance);
    }

    public boolean isActive() {
        return LoanStatus.ACTIVE == status;
    }

    public boolean isSettled() {
        return LoanStatus.SETTLED == status;
    }

    // ── Domain behaviour ──────────────────────────────────────────────────

    /**
     * Marks the loan as disbursed. Transitions from APPROVED → ACTIVE.
     * For FLAT_RATE loans, accruedInterest is set immediately to totalInterestCharged.
     */
    public void disburse(String mpesaRef, UUID disbursedBy) {
        if (this.status != LoanStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot disburse loan in status: " + this.status);
        }
        this.principalBalance = this.principalAmount;
        this.disbursementMpesaRef = mpesaRef;
        this.disbursedAt = Instant.now();
        this.disbursedBy = disbursedBy;
        this.status = LoanStatus.ACTIVE;
    }

    /**
     * Accrues interest for one period.
     * Called by the InterestAccrualScheduler based on the product's accrual frequency.
     * For FLAT_RATE products this should NOT be called — interest is set at disbursement.
     */
    public void accrueInterest(Money interestAmount) {
        this.accruedInterest = this.accruedInterest.add(interestAmount);
    }

    /**
     * Records a repayment. Allocation order:
     *   1. Penalties first (incentivises timely payment)
     *   2. Accrued interest
     *   3. Principal last
     *
     * Returns a RepaymentAllocation showing how the payment was split.
     */
    public RepaymentAllocation applyRepayment(Money payment, String mpesaRef) {
        if (!isActive()) {
            throw new IllegalStateException("Cannot repay a loan that is not active");
        }
        if (payment.isGreaterThan(totalOutstanding())) {
            throw new IllegalArgumentException(
                    "Payment of " + payment + " exceeds total outstanding of " + totalOutstanding());
        }

        Money remaining = payment;
        Money toPenalty = Money.ofKes(java.math.BigDecimal.ZERO);
        Money toInterest = Money.ofKes(java.math.BigDecimal.ZERO);
        Money toPrincipal = Money.ofKes(java.math.BigDecimal.ZERO);

        // 1. Penalties
        if (remaining.isPositive() && penaltyBalance.isPositive()) {
            toPenalty = remaining.isGreaterThan(penaltyBalance) ? penaltyBalance : remaining;
            this.penaltyBalance = this.penaltyBalance.subtract(toPenalty);
            remaining = remaining.subtract(toPenalty);
        }

        // 2. Interest
        if (remaining.isPositive() && accruedInterest.isPositive()) {
            toInterest = remaining.isGreaterThan(accruedInterest) ? accruedInterest : remaining;
            this.accruedInterest = this.accruedInterest.subtract(toInterest);
            this.totalInterestRepaid = this.totalInterestRepaid.add(toInterest);
            remaining = remaining.subtract(toInterest);
        }

        // 3. Principal
        if (remaining.isPositive()) {
            toPrincipal = remaining.isGreaterThan(principalBalance) ? principalBalance : remaining;
            this.principalBalance = this.principalBalance.subtract(toPrincipal);
            this.totalPrincipalRepaid = this.totalPrincipalRepaid.add(toPrincipal);
        }

        // Auto-settle if fully paid
        if (totalOutstanding().isZero()) {
            this.status = LoanStatus.SETTLED;
            this.settledAt = Instant.now();
        }

        return new RepaymentAllocation(payment, toPenalty, toInterest, toPrincipal, mpesaRef);
    }

    /**
     * Applies a late payment penalty.
     */
    public void applyPenalty(Money penaltyAmount) {
        this.penaltyBalance = this.penaltyBalance.add(penaltyAmount);
    }

    public void markDefaulted() {
        this.status = LoanStatus.DEFAULTED;
    }

    public void writeOff(String reason) {
        this.status = LoanStatus.WRITTEN_OFF;
        this.settlementNote = reason;
        this.settledAt = Instant.now();
    }

    // ── Records ───────────────────────────────────────────────────────────

    public record RepaymentAllocation(
            Money totalPayment,
            Money toPenalty,
            Money toInterest,
            Money toPrincipal,
            String mpesaRef
    ) {}
}