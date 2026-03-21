package com.pesaloop.contribution.domain.service;

import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.shared.domain.Money;

import java.math.BigDecimal;

/**
 * Domain service — allocates an incoming payment across arrears and current period.
 *
 * This logic does not belong to ContributionEntry alone because the allocation
 * policy (arrears-first vs current-first) is a group-level rule, not an entry rule.
 * The service encapsulates that decision.
 *
 * Allocation order (arrears-first, the most common chamaa policy):
 *   1. Clear oldest arrears first
 *   2. Apply remainder to current period entry
 *   3. If surplus remains, return it (handled by caller)
 */
public class ContributionAllocationService {

    public enum AllocationPolicy { ARREARS_FIRST, CURRENT_FIRST }

    /**
     * Allocates a payment to a contribution entry.
     * Returns the remaining surplus (zero in most cases).
     */
    public AllocationResult allocate(
            ContributionEntry entry,
            Money payment,
            Money arrearsBalance,
            AllocationPolicy policy) {

        Money remaining = payment;
        Money toArrears = Money.ofKes(BigDecimal.ZERO);
        Money toCurrent = Money.ofKes(BigDecimal.ZERO);

        if (policy == AllocationPolicy.ARREARS_FIRST && arrearsBalance.isPositive()) {
            toArrears = remaining.isGreaterThan(arrearsBalance) ? arrearsBalance : remaining;
            remaining = remaining.subtract(toArrears);
        }

        if (remaining.isPositive()) {
            Money stillOwed = entry.getExpectedAmount().subtract(entry.getPaidAmount());
            toCurrent = remaining.isGreaterThan(stillOwed) ? stillOwed : remaining;
            remaining = remaining.subtract(toCurrent);
        }

        Money surplus = remaining;
        boolean fullyClear = entry.getPaidAmount().add(toCurrent)
                .isGreaterThanOrEqual(entry.getExpectedAmount());

        return new AllocationResult(toArrears, toCurrent, surplus, fullyClear);
    }

    public record AllocationResult(
            Money toArrears,
            Money toCurrent,
            Money surplus,
            boolean entryFullyCovered
    ) {}
}
