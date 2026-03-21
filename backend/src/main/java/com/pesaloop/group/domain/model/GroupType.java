package com.pesaloop.group.domain.model;

/**
 * The operational model of a Chamaa group.
 * A group can combine multiple types — e.g., MERRY_GO_ROUND + WELFARE is very common.
 * Stored as a set on the Group aggregate.
 */
public enum GroupType {
    /**
     * Members contribute to a pool; payouts rotate one member per cycle.
     * No loans. Each member receives exactly once per full rotation.
     */
    MERRY_GO_ROUND,

    /**
     * Members contribute to a shared kitty and can borrow from it.
     * At year-end, savings + interest earned on loans are distributed
     * proportionally by shares owned.
     */
    TABLE_BANKING,

    /**
     * Members save toward a shared goal (land, equipment, event).
     * Funds are locked until target is reached or majority vote releases them.
     */
    INVESTMENT,

    /**
     * Members pay into a welfare kitty.
     * Payouts triggered by qualifying life events (bereavement, illness, etc.)
     */
    WELFARE
}
