package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "loan_products")
@Getter
@Setter
public class LoanProductJpaEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false)
    private InterestType interestType = InterestType.FLAT;

    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_frequency", nullable = false)
    private InterestAccrualFrequency accrualFrequency = InterestAccrualFrequency.FLAT_RATE;

    @Column(name = "interest_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "custom_accrual_interval_days")
    private Integer customAccrualIntervalDays;

    @Column(name = "minimum_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumAmount = BigDecimal.ZERO;

    @Column(name = "maximum_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maximumAmount;

    @Column(name = "max_multiple_of_savings", precision = 6, scale = 2)
    private BigDecimal maxMultipleOfSavings;

    @Column(name = "max_multiple_of_shares_value", precision = 6, scale = 2)
    private BigDecimal maxMultipleOfSharesValue;

    @Column(name = "max_repayment_periods", nullable = false)
    private int maxRepaymentPeriods = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false)
    private InterestAccrualFrequency repaymentFrequency = InterestAccrualFrequency.MONTHLY;

    @Column(name = "bullet_repayment", nullable = false)
    private boolean bulletRepayment = false;

    @Column(name = "minimum_membership_months", nullable = false)
    private int minimumMembershipMonths = 0;

    @Column(name = "minimum_shares_owned", nullable = false)
    private int minimumSharesOwned = 1;

    @Column(name = "requires_guarantor", nullable = false)
    private boolean requiresGuarantor = false;

    @Column(name = "max_guarantors", nullable = false)
    private int maxGuarantors = 1;

    @Column(name = "requires_zero_arrears", nullable = false)
    private boolean requiresZeroArrears = true;

    @Column(name = "max_concurrent_loans", nullable = false)
    private int maxConcurrentLoans = 1;

    @Column(name = "late_repayment_penalty_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal lateRepaymentPenaltyRate = new BigDecimal("0.0500");

    @Column(name = "penalty_grace_period_days", nullable = false)
    private int penaltyGracePeriodDays = 3;

    @Column(name = "created_by")
    private UUID createdBy;
}
