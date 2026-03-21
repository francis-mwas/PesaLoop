package com.pesaloop.group.domain.model;

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for Merry-Go-Round (MGR) payout behaviour.
 * Embedded in the Group aggregate.
 */
@Value
@Builder
public class MgrPayoutConfig {

    /**
     * How the rotation order is determined.
     */
    MgrRotationStrategy rotationStrategy;

    /**
     * When to trigger the payout to the current beneficiary.
     */
    MgrPayoutTrigger payoutTrigger;

    /**
     * If true and a member hasn't paid when the cycle should close,
     * the cycle is delayed until they pay (or the grace period expires).
     * If false, the payout proceeds and the late member accrues a debt.
     */
    boolean waitForAllBeforePayout;

    /**
     * Whether members can voluntarily swap their rotation position.
     * Swaps must be approved by both parties + admin.
     */
    boolean allowPositionSwaps;

    /**
     * Whether a member who has already received their payout
     * can receive again in a subsequent rotation within the same year.
     * Usually false — each member gets exactly one turn per full rotation.
     */
    boolean allowMultiplePayoutsPerYear;

    public enum MgrRotationStrategy {
        FIXED_ORDER,          // admin sets the order manually
        RANDOM_DRAW,          // system randomly assigns order at start of each rotation
        JOINING_ORDER,        // first to join = first to receive
        BID,                  // members bid for their preferred slot (rare)
        SENIORITY             // most senior member (longest membership) goes first
    }

    public enum MgrPayoutTrigger {
        ALL_MEMBERS_PAID,     // payout only when every member has contributed for this cycle
        DUE_DATE_REACHED,     // payout on the cycle's due date regardless of who has paid
        MANUAL_APPROVAL       // admin manually approves each payout
    }
}
