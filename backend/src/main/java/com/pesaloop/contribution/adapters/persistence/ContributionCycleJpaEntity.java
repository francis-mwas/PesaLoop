package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contribution_cycles")
@Getter
@Setter
public class ContributionCycleJpaEntity extends BaseEntity {

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "grace_period_end", nullable = false)
    private LocalDate gracePeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionCycle.CycleStatus status = ContributionCycle.CycleStatus.OPEN;

    @Column(name = "total_expected_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalExpectedAmount = BigDecimal.ZERO;

    @Column(name = "total_collected_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCollectedAmount = BigDecimal.ZERO;

    @Column(name = "total_arrears_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalArrearsAmount = BigDecimal.ZERO;

    @Column(name = "total_fines_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalFinesAmount = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "KES";

    @Column(name = "mgr_beneficiary_id")
    private UUID mgrBeneficiaryId;

    @Column(name = "mgr_payout_amount", precision = 15, scale = 2)
    private BigDecimal mgrPayoutAmount;

    @Column(name = "mgr_paid_out_at")
    private Instant mgrPaidOutAt;

    @Column(name = "mgr_mpesa_ref", length = 30)
    private String mgrMpesaRef;
}
