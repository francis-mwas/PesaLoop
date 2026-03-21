package com.pesaloop.contribution.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Records one member's contribution payment for a specific cycle.
 *
 * Lifecycle: PENDING → PAID (or LATE or WAIVED)
 *
 * Key design decisions from the real chamaa data:
 * 1. expectedAmount is stored at time of cycle creation (snapshot of shares × price)
 *    so changing share price mid-year doesn't retroactively alter old records.
 * 2. A member may make partial payments — e.g. pay KES 30,000 today
 *    and KES 21,000 next week. Each payment is a separate PaymentRecord
 *    linked to this entry; paidAmount is the running total.
 * 3. The ARREARS_APPLIED status means the unpaid portion has been moved
 *    to the member's arrearsBalance for tracking.
 */
@Getter
@Builder
public class ContributionEntry {

    private final UUID id;
    private final UUID groupId;
    private final UUID cycleId;
    private final UUID memberId;

    // Snapshot of how much this member was expected to pay this cycle
    // (shares × pricePerShare at the time the cycle was opened)
    private final Money expectedAmount;

    private Money paidAmount;               // running total of payments received
    private Money arrearsCarriedForward;    // arrears from previous cycles included here

    private EntryStatus status;
    private Instant firstPaymentAt;
    private Instant fullyPaidAt;

    // Payment method of the most recent payment
    private PaymentMethod lastPaymentMethod;
    private String lastMpesaReference;
    private UUID recordedBy;                // null = auto via M-Pesa webhook

    // ── Domain behaviour ─────────────────────────────────────────────────────

    public Money balance() {
        return expectedAmount.subtract(paidAmount);
    }

    public boolean isFullyPaid() {
        return paidAmount.isGreaterThanOrEqual(expectedAmount);
    }

    public void recordPayment(Money amount, PaymentMethod method, String mpesaRef, UUID recordedBy) {
        if (this.firstPaymentAt == null) this.firstPaymentAt = Instant.now();

        // Apply to arrears first, then to current cycle amount
        this.paidAmount = this.paidAmount.add(amount);
        this.lastPaymentMethod = method;
        this.lastMpesaReference = mpesaRef;
        this.recordedBy = recordedBy;

        if (isFullyPaid()) {
            this.fullyPaidAt = Instant.now();
            this.status = EntryStatus.PAID;
        } else {
            this.status = EntryStatus.PARTIAL;
        }
    }

    public void markLate() {
        if (status != EntryStatus.PAID) {
            this.status = EntryStatus.LATE;
        }
    }

    public void waive(UUID approvedBy) {
        this.status = EntryStatus.WAIVED;
    }

    public void moveToArrears() {
        this.arrearsCarriedForward = balance();
        this.status = EntryStatus.ARREARS_APPLIED;
    }

    public enum EntryStatus {
        PENDING,            // cycle is open, payment not yet made
        PARTIAL,            // partial payment received
        PAID,               // fully paid
        LATE,               // due date passed, not fully paid, grace period expired
        WAIVED,             // admin waived the contribution (illness, etc.)
        ARREARS_APPLIED     // unpaid amount moved to member's arrears ledger
    }

    public enum PaymentMethod {
        MPESA_STK_PUSH,     // initiated from PesaLoop (STK push)
        MPESA_PAYBILL,      // member paid directly to paybill
        MPESA_TILL,         // member paid to till number
        CASH,               // physical cash, recorded manually
        BANK_TRANSFER,
        INTERNAL_TRANSFER   // from member's savings/overdraft balance in the system
    }
}
