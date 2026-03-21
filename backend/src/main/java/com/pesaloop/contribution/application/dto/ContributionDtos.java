package com.pesaloop.contribution.application.dto;

import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.contribution.domain.model.ContributionEntry.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTOs for contribution use cases.
 * All records are immutable — safe to pass across layers.
 */
public final class ContributionDtos {
    private ContributionDtos() {}

    // ── Requests ──────────────────────────────────────────────────────────────

    /**
     * Admin or treasurer manually records a cash/bank payment for a member.
     * Used when the member did not pay via M-Pesa (e.g. cash to treasurer).
     */
    public record RecordManualPaymentRequest(
            @NotNull UUID memberId,
            @NotNull UUID cycleId,
            @NotNull @Positive BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            String reference,          // bank ref, receipt number, etc.
            String notes
    ) {}

    /**
     * Initiates an M-Pesa STK Push to a member's phone.
     * The member receives a PIN prompt and confirms on their handset.
     */
    public record InitiateStkPushRequest(
            @NotNull UUID memberId,
            @NotNull UUID cycleId,
            /** Amount to collect. If null, defaults to the member's outstanding balance for this cycle. */
            BigDecimal amount,
            /** Phone to send the push to. Defaults to member's registered phone if null. */
            @Pattern(regexp = "^254[0-9]{9}$", message = "Phone must be in 254XXXXXXXXX format")
            String phoneNumber
    ) {}

    // ── Responses ─────────────────────────────────────────────────────────────

    public record StkPushResponse(
            String checkoutRequestId,
            String merchantRequestId,
            String customerMessage,
            UUID memberId,
            BigDecimal amount,
            String phoneNumber
    ) {}

    public record ContributionEntryResponse(
            UUID id,
            UUID cycleId,
            UUID memberId,
            String memberName,
            String memberNumber,
            BigDecimal expectedAmount,
            BigDecimal paidAmount,
            BigDecimal balance,
            BigDecimal arrearsCarriedForward,
            ContributionEntry.EntryStatus status,
            PaymentMethod lastPaymentMethod,
            String lastMpesaReference,
            Instant firstPaymentAt,
            Instant fullyPaidAt
    ) {}

    public record CycleSummaryResponse(
            UUID cycleId,
            int cycleNumber,
            int financialYear,
            LocalDate dueDate,
            LocalDate gracePeriodEnd,
            String status,
            BigDecimal totalExpected,
            BigDecimal totalCollected,
            BigDecimal totalOutstanding,
            int totalMembers,
            int paidCount,
            int pendingCount,
            int lateCount,
            // MGR-specific
            UUID mgrBeneficiaryMemberId,
            String mgrBeneficiaryName,
            BigDecimal mgrPayoutAmount
    ) {}

    public record MemberStatementResponse(
            UUID memberId,
            String memberName,
            String memberNumber,
            int sharesOwned,
            BigDecimal totalSaved,      // lifetime savings
            BigDecimal arrearsBalance,
            BigDecimal finesBalance,
            java.util.List<ContributionEntryResponse> entries
    ) {}
}
