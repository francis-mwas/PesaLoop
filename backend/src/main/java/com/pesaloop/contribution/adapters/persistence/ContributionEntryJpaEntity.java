package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.shared.adapters.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contribution_entries")
@Getter
@Setter
public class ContributionEntryJpaEntity extends BaseEntity {

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "expected_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "arrears_carried_forward", nullable = false, precision = 15, scale = 2)
    private BigDecimal arrearsCarriedForward = BigDecimal.ZERO;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "KES";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionEntry.EntryStatus status = ContributionEntry.EntryStatus.PENDING;

    @Column(name = "first_payment_at")
    private Instant firstPaymentAt;

    @Column(name = "fully_paid_at")
    private Instant fullyPaidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_payment_method", length = 25)
    private ContributionEntry.PaymentMethod lastPaymentMethod;

    @Column(name = "last_mpesa_reference", length = 30)
    private String lastMpesaReference;

    @Column(name = "recorded_by")
    private UUID recordedBy;
}
