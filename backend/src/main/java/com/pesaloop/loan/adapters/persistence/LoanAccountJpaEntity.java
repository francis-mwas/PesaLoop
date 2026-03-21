package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_accounts")
@Getter
@Setter
public class LoanAccountJpaEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "loan_reference", nullable = false, unique = true)
    private String loanReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status = LoanStatus.APPLICATION_SUBMITTED;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "total_interest_charged", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInterestCharged = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "KES";

    @Column(name = "disbursement_date")
    private LocalDate disbursementDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // Running balances
    @Column(name = "principal_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalBalance = BigDecimal.ZERO;

    @Column(name = "accrued_interest", nullable = false, precision = 15, scale = 2)
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Column(name = "total_interest_repaid", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInterestRepaid = BigDecimal.ZERO;

    @Column(name = "total_principal_repaid", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPrincipalRepaid = BigDecimal.ZERO;

    @Column(name = "penalty_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal penaltyBalance = BigDecimal.ZERO;

    @Column(name = "application_note")
    private String applicationNote;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "disbursement_mpesa_ref", length = 30)
    private String disbursementMpesaRef;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "disbursed_by")
    private UUID disbursedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settlement_note")
    private String settlementNote;
}
