package com.pesaloop.group.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Configures the share-based contribution system for a group.
 *
 * Real-world example from the chamaa spreadsheet:
 *   - 1 share = KES 3,000
 *   - Minimum shares = 1  (Sarah Wachera — KES 3,000/month)
 *   - Maximum shares = 25 (Daniel Karoki  — KES 75,000/month)
 *   - Alice Watiri has 17 shares (KES 51,000/month)
 *
 * How this drives everything else:
 *   - Monthly contribution = sharePrice × sharesOwned
 *   - Loan eligibility = sharesOwned × loanMultiplier (e.g. up to 3× your shares value)
 *   - Year-end dividend is proportional to shares owned
 *   - Voting power (optional) can also be proportional to shares
 *
 * This config can be updated at any time by the group admin.
 * Historical share prices are versioned via ShareConfigHistory to ensure
 * past contribution calculations remain accurate.
 */
@Value
@Builder
public class ShareConfig {

    /**
     * The monetary value of one share. E.g. Money.ofKes(3000).
     * Changing this takes effect from the next contribution cycle.
     */
    Money pricePerShare;

    /**
     * Minimum number of shares a member must hold.
     * Usually 1. Some groups require a minimum (e.g. 2 shares minimum).
     */
    int minimumShares;

    /**
     * Maximum number of shares any single member can hold.
     * Prevents concentration of power/interest in one member.
     * E.g. max = 25 → max contribution = 25 × KES 3,000 = KES 75,000/month
     * Set to Integer.MAX_VALUE if there is no cap.
     */
    int maximumShares;

    /**
     * If true, member contributions MUST be an exact multiple of pricePerShare.
     * If false, members can contribute arbitrary amounts (flat-amount mode — no shares).
     */
    boolean sharesMode;

    /**
     * Whether share count can be increased mid-year.
     * Some groups freeze share changes during the financial year.
     */
    boolean allowShareChangeMidYear;

    /**
     * Maximum total shares allowed across ALL members combined.
     * 0 = no cap enforced. Prevents any single member from holding
     * a disproportionate share of the group's contribution pool.
     */
    int maxTotalGroupShares;

    /**
     * Validates that the given share count is within group rules.
     */
    public void validateShareCount(int shares) {
        if (shares < minimumShares) {
            throw new IllegalArgumentException(
                "Share count %d is below minimum of %d".formatted(shares, minimumShares));
        }
        if (shares > maximumShares) {
            throw new IllegalArgumentException(
                "Share count %d exceeds maximum of %d".formatted(shares, maximumShares));
        }
    }

    /**
     * Calculates the periodic contribution amount for a given number of shares.
     */
    public Money contributionFor(int shares) {
        validateShareCount(shares);
        return pricePerShare.multiply(shares);
    }

    /**
     * Calculates a member's proportional share of a pool (e.g. interest pool).
     * memberShares / totalGroupShares × pool
     */
    public Money proportionalShare(int memberShares, int totalGroupShares, Money pool) {
        if (totalGroupShares == 0) return Money.ofKes(BigDecimal.ZERO);
        BigDecimal ratio = BigDecimal.valueOf(memberShares)
                .divide(BigDecimal.valueOf(totalGroupShares), 10, java.math.RoundingMode.HALF_EVEN);
        return pool.multiply(ratio);
    }

    /** Creates a simple flat-amount config (no shares system, equal contributions). */
    public static ShareConfig flatAmount(Money fixedAmount) {
        return ShareConfig.builder()
                .pricePerShare(fixedAmount)
                .minimumShares(1)
                .maximumShares(1)
                .sharesMode(false)
                .allowShareChangeMidYear(false)
                .build();
    }
}
