package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.domain.model.RepaymentInstallment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "repayment_installments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class RepaymentInstallmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestDue;

    @Column(name = "total_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDue;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "principal_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false, precision = 15, scale = 2)
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepaymentInstallment.InstallmentStatus status = RepaymentInstallment.InstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "mpesa_ref", length = 30)
    private String mpesaRef;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
