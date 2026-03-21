package com.pesaloop.group.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.*;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Group aggregate root — represents a single Chamaa.
 *
 * This is a pure domain object (no JPA, no Spring annotations).
 * Persistence is handled by GroupJpaEntity in the infrastructure layer.
 */
@Getter
@Builder
@AllArgsConstructor
public class Group {

    private final UUID id;
    private String name;
    private String slug;                    // URL-friendly identifier e.g. "wanjiku-welfare-2024"
    private String description;

    // Multi-type support — a group can be both MERRY_GO_ROUND and WELFARE
    private Set<GroupType> types;

    private GroupStatus status;
    private String currencyCode;            // KES, USD, GBP, etc.

    // Share / contribution configuration
    private ShareConfig shareConfig;
    private ContributionFrequency contributionFrequency;
    private Integer customFrequencyDays;    // only used when frequency = CUSTOM

    // Financial year settings
    private LocalDate financialYearStart;   // e.g. January 1 each year
    private LocalDate financialYearEnd;

    // M-Pesa configuration
    private String mpesaShortcode;          // Paybill or Till number
    private String mpesaShortcodeType;      // PAYBILL | TILL

    // Registration details (for formal groups)
    private String registrationNumber;
    private String physicalAddress;
    private String county;

    // Operational settings
    private int gracePeriodDays;            // days before a late fee is applied
    private boolean requiresGuarantorForLoans;
    private int maxActiveLoansPerMember;

    // Payout configuration for merry-go-round
    private MgrPayoutConfig mgrPayoutConfig;

    private UUID createdBy;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;

    // ── Domain behaviour ─────────────────────────────────────────────────────

    public boolean isOfType(GroupType type) {
        return types != null && types.contains(type);
    }

    public boolean isActive() {
        return GroupStatus.ACTIVE == status;
    }

    public boolean isMerryGoRound() {
        return isOfType(GroupType.MERRY_GO_ROUND);
    }

    public boolean isTableBanking() {
        return isOfType(GroupType.TABLE_BANKING);
    }

    public boolean allowsLoans() {
        return isOfType(GroupType.TABLE_BANKING);
    }

    /** Returns the number of days between contribution due dates. */
    public int contributionIntervalDays() {
        return switch (contributionFrequency) {
            case DAILY       -> 1;
            case WEEKLY      -> 7;
            case FORTNIGHTLY -> 14;
            case MONTHLY     -> 30;  // approximate; due-date logic uses calendar months
            case QUARTERLY   -> 91;
            case ANNUALLY    -> 365;
            case CUSTOM      -> customFrequencyDays != null ? customFrequencyDays : 30;
        };
    }

    /** Returns the per-period contribution amount for a member with the given shares. */
    public Money contributionAmountFor(int shares) {
        return shareConfig.contributionFor(shares);
    }

    public void activate() {
        this.status = GroupStatus.ACTIVE;
    }

    public void suspend(String reason) {
        this.status = GroupStatus.SUSPENDED;
    }

    public void close() {
        this.status = GroupStatus.CLOSED;
    }

    public void updateShareConfig(ShareConfig newConfig) {
        // Validation: can't reduce price below existing commitments mid-year
        // if the group doesn't allow mid-year changes
        this.shareConfig = newConfig;
    }

    // ── Factory method ────────────────────────────────────────────────────────

    public static Group create(
            String name,
            String slug,
            Set<GroupType> types,
            String currencyCode,
            ShareConfig shareConfig,
            ContributionFrequency frequency,
            UUID createdBy) {

        if (types == null || types.isEmpty()) {
            throw new IllegalArgumentException("A group must have at least one type");
        }

        return Group.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .types(EnumSet.copyOf(types))
                .status(GroupStatus.PENDING_SETUP)
                .currencyCode(currencyCode)
                .shareConfig(shareConfig)
                .contributionFrequency(frequency)
                .gracePeriodDays(3)
                .requiresGuarantorForLoans(true)
                .maxActiveLoansPerMember(2)
                .createdBy(createdBy)
                .createdAt(java.time.Instant.now())
                .build();
    }
}
