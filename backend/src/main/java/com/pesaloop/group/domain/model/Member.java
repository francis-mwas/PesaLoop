package com.pesaloop.group.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A Member is a person's participation in a specific Group.
 * One User can be a member of many Groups (different Member records).
 *
 * Share ownership model:
 * - sharesOwned:  current number of shares this member holds
 * - Each share = shareConfig.pricePerShare (e.g. KES 3,000)
 * - Monthly contribution = sharesOwned × pricePerShare
 * - Example: 17 shares × KES 3,000 = KES 51,000/month (Alice Watiri)
 */
@Getter
@Builder
public class Member {

    private final UUID id;
    private final UUID groupId;
    private final UUID userId;          // → User in Identity context

    private String memberNumber;        // Human-readable e.g. "M-017"
    private MemberRole role;
    private MemberStatus status;

    // Share ownership — the core of the share-based contribution model
    private int sharesOwned;
    private LocalDate sharesLastChangedOn;

    // Contribution configuration
    // In non-shares mode (flat-amount groups), this overrides the share calculation
    private Money customContributionAmount;  // null = use shares calculation

    // Running balances (denormalized for performance — kept in sync by domain events)
    private Money savingsBalance;           // total contributed to date
    private Money arrearsBalance;           // overdue contributions not yet paid
    private Money finesBalance;             // unpaid fines

    // Personal details
    private LocalDate joinedOn;
    private String nationalId;
    private String phoneNumber;

    // Next of kin (critical for welfare groups)
    private String nextOfKinName;
    private String nextOfKinPhone;
    private String nextOfKinRelationship;

    // Merry-go-round
    private Integer mgrPosition;           // null if not in rotation yet
    private MgrSlotStatus mgrSlotStatus;

    // ── Domain behaviour ─────────────────────────────────────────────────────

    /**
     * Calculates the contribution amount due for one period.
     * Uses custom amount if set (flat-amount mode), otherwise derives from shares.
     */
    public Money periodContributionAmount(ShareConfig shareConfig) {
        if (customContributionAmount != null) {
            return customContributionAmount;
        }
        return shareConfig.contributionFor(sharesOwned);
    }

    /**
     * Updates the member's share count.
     * Validates against the group's ShareConfig rules.
     */
    public void updateShares(int newShareCount, ShareConfig shareConfig, boolean midYear) {
        if (!shareConfig.isAllowShareChangeMidYear() && midYear) {
            throw new IllegalStateException(
                "This group does not allow share changes during the financial year");
        }
        shareConfig.validateShareCount(newShareCount);
        this.sharesOwned = newShareCount;
        this.sharesLastChangedOn = LocalDate.now();
    }

    public void creditSavings(Money amount) {
        this.savingsBalance = this.savingsBalance.add(amount);
    }

    public void debitSavings(Money amount) {
        if (amount.isGreaterThan(this.savingsBalance)) {
            throw new IllegalStateException("Insufficient savings balance");
        }
        this.savingsBalance = this.savingsBalance.subtract(amount);
    }

    public void addArrears(Money amount) {
        this.arrearsBalance = this.arrearsBalance.add(amount);
    }

    public void clearArrears(Money amount) {
        Money cleared = amount.isGreaterThan(arrearsBalance) ? arrearsBalance : amount;
        this.arrearsBalance = this.arrearsBalance.subtract(cleared);
    }

    public void addFine(Money amount) {
        this.finesBalance = this.finesBalance.add(amount);
    }

    public boolean isActive() {
        return MemberStatus.ACTIVE == status;
    }

    public boolean hasArrears() {
        return arrearsBalance != null && arrearsBalance.isPositive();
    }

    public enum MgrSlotStatus {
        PENDING,    // has not yet received payout in current rotation
        SERVED,     // has received payout in current rotation
        SKIPPED     // was skipped (late payment, etc.)
    }
}
