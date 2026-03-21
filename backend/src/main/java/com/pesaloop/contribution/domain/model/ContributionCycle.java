package com.pesaloop.contribution.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents one contribution period (cycle) for a group.
 *
 * From the chamaa spreadsheet, each month column is one ContributionCycle.
 * The cycle holds group-level totals, while individual ContributionEntry
 * records hold each member's payment for this cycle.
 *
 * Cycle lifecycle:
 *   OPEN → contributions are being collected
 *   GRACE_PERIOD → due date passed, but grace period not yet expired
 *   CLOSED → all contributions finalised; latecomers now in arrears
 *   PAYOUT_READY → (MGR only) all members paid; payout can be triggered
 *   PAID_OUT → (MGR only) payout disbursed to beneficiary
 */
@Getter
@Builder
public class ContributionCycle {

    private final UUID id;
    private final UUID groupId;
    private final int cycleNumber;          // 1 = first cycle, 2 = second, etc.
    private final int year;                 // financial year this cycle belongs to
    private final LocalDate dueDate;        // when contributions are due
    private final LocalDate gracePeriodEnd; // last day before late fees apply

    private CycleStatus status;

    // Group-level financial summary (maintained incrementally as payments arrive)
    private Money totalExpected;            // sum of all members' expected contributions
    private Money totalCollected;           // sum of all contributions actually received
    private Money totalArrears;             // total overdue from previous cycles
    private Money totalFinesIssued;

    // MGR-specific
    private UUID mgrBeneficiaryMemberId;    // which member receives payout this cycle
    private Money mgrPayoutAmount;
    private java.time.Instant mgrPaidOutAt;

    // ── Domain behaviour ─────────────────────────────────────────────────────

    public boolean isOpen() {
        return CycleStatus.OPEN == status || CycleStatus.GRACE_PERIOD == status;
    }

    public boolean isPaidOut() {
        return CycleStatus.PAID_OUT == status;
    }

    public Money shortfall() {
        return totalExpected.subtract(totalCollected);
    }

    public boolean isFullyPaid() {
        return totalCollected.isGreaterThanOrEqual(totalExpected);
    }

    public void recordContribution(Money amount) {
        this.totalCollected = this.totalCollected.add(amount);
    }

    public void close() {
        this.status = CycleStatus.CLOSED;
        // Any shortfall becomes arrears for the affected members (handled in domain event)
    }

    public void markPayoutReady() {
        if (!isFullyPaid()) throw new IllegalStateException("Cycle is not fully paid");
        this.status = CycleStatus.PAYOUT_READY;
    }

    public void markPaidOut(java.time.Instant paidAt) {
        this.mgrPaidOutAt = paidAt;
        this.status = CycleStatus.PAID_OUT;
    }

    public enum CycleStatus {
        OPEN,
        GRACE_PERIOD,
        CLOSED,
        PAYOUT_READY,
        PAID_OUT
    }
}
