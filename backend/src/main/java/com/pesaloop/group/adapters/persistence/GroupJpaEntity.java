package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.domain.model.*;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for the groups table.
 * Deliberately separate from the Group domain model — infrastructure detail.
 * Mapper converts between the two.
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
public class GroupJpaEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private GroupStatus status = GroupStatus.PENDING_SETUP;

    @Column(nullable = false, length = 3)
    private String currencyCode = "KES";

    // ── Share config (embedded) ────────────────────────────────────────────
    @Column(name = "share_price_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal sharePriceAmount = BigDecimal.ZERO;

    @Column(name = "share_price_currency", nullable = false, length = 3)
    private String sharePriceCurrency = "KES";

    @Column(name = "minimum_shares", nullable = false)
    private int minimumShares = 1;

    @Column(name = "maximum_shares", nullable = false)
    private int maximumShares = Integer.MAX_VALUE;

    @Column(name = "shares_mode", nullable = false)
    private boolean sharesMode = true;

    @Column(name = "allow_share_change_mid_year", nullable = false)
    private boolean allowShareChangeMidYear = false;

    // ── Contribution schedule ─────────────────────────────────────────────
    @Column(name = "contribution_frequency", nullable = false)
    @Enumerated(EnumType.STRING)
    private ContributionFrequency contributionFrequency = ContributionFrequency.MONTHLY;

    @Column(name = "custom_frequency_days")
    private Integer customFrequencyDays;

    // ── Group types (stored as Postgres array) ────────────────────────────
    @Column(name = "group_types", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] groupTypes = {"TABLE_BANKING"};

    // ── Financial year ────────────────────────────────────────────────────
    @Column(name = "financial_year_start_month", nullable = false)
    private int financialYearStartMonth = 1;

    @Column(name = "financial_year_start_day", nullable = false)
    private int financialYearStartDay = 1;

    // ── M-Pesa ────────────────────────────────────────────────────────────
    @Column(name = "mpesa_shortcode", length = 20)
    private String mpesaShortcode;

    @Column(name = "mpesa_shortcode_type", length = 10)
    private String mpesaShortcodeType;

    // ── Operational settings ──────────────────────────────────────────────
    @Column(name = "grace_period_days", nullable = false)
    private int gracePeriodDays = 3;

    @Column(name = "requires_guarantor_for_loans", nullable = false)
    private boolean requiresGuarantorForLoans = true;

    @Column(name = "max_active_loans_per_member", nullable = false)
    private int maxActiveLoansPerMember = 2;

    // ── MGR settings ──────────────────────────────────────────────────────
    @Column(name = "mgr_rotation_strategy")
    @Enumerated(EnumType.STRING)
    private MgrPayoutConfig.MgrRotationStrategy mgrRotationStrategy
            = MgrPayoutConfig.MgrRotationStrategy.FIXED_ORDER;

    @Column(name = "mgr_payout_trigger")
    @Enumerated(EnumType.STRING)
    private MgrPayoutConfig.MgrPayoutTrigger mgrPayoutTrigger
            = MgrPayoutConfig.MgrPayoutTrigger.ALL_MEMBERS_PAID;

    @Column(name = "mgr_wait_for_all", nullable = false)
    private boolean mgrWaitForAll = true;

    @Column(name = "mgr_allow_position_swaps", nullable = false)
    private boolean mgrAllowPositionSwaps = false;

    // ── Registration ──────────────────────────────────────────────────────
    @Column(name = "registration_number", length = 50)
    private String registrationNumber;

    @Column(name = "physical_address")
    private String physicalAddress;

    @Column(name = "county", length = 50)
    private String county;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
