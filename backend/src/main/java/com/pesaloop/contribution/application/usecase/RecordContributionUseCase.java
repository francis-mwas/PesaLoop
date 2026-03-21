package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.RecordContributionPort;

import com.pesaloop.contribution.application.dto.ContributionDtos.*;
import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.contribution.application.port.out.ContributionCycleRepository;
import com.pesaloop.contribution.application.port.out.ContributionEntryRepository;
import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records a manual (non-M-Pesa) contribution payment.
 * Used when a member pays in cash to the treasurer, or via bank transfer.
 *
 * Business rules enforced:
 * 1. Cycle must be OPEN or GRACE_PERIOD — cannot record into a CLOSED cycle.
 * 2. Payment cannot exceed what is still owed on the entry.
 * 3. If the member has arrears, any excess beyond the current cycle balance
 *    is automatically applied to arrears first (configurable).
 * 4. Every recorded payment is appended to the audit log.
 * 5. Member's savings_balance and cycle total_collected are updated atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordContributionUseCase implements RecordContributionPort {

    private final ContributionCycleRepository cycleRepository;
    private final ContributionEntryRepository entryRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ContributionEntryResponse execute(
            RecordManualPaymentRequest request,
            UUID recordedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        // ── 1. Load and validate the cycle ────────────────────────────────────
        ContributionCycle cycle = cycleRepository.findById(request.cycleId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cycle not found: " + request.cycleId()));

        if (!cycle.isOpen()) {
            throw new IllegalStateException(
                    "Cannot record payment — cycle %d is %s"
                            .formatted(cycle.getCycleNumber(), cycle.getStatus()));
        }

        // ── 2. Load and validate the member ───────────────────────────────────
        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found: " + request.memberId()));

        if (!member.isActive()) {
            throw new IllegalStateException(
                    "Cannot record payment for %s member"
                            .formatted(member.getStatus().name().toLowerCase()));
        }

        // ── 3. Load the contribution entry ────────────────────────────────────
        ContributionEntry entry = entryRepository
                .findByCycleIdAndMemberId(request.cycleId(), request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No contribution entry found for member %s in cycle %s"
                                .formatted(request.memberId(), request.cycleId())));

        // ── 4. Guard — entry already fully paid ──────────────────────────────────
        if (entry.isFullyPaid()) {
            throw new IllegalStateException(
                    "Member has already fully paid for cycle " + cycle.getCycleNumber());
        }

        Money payment = Money.of(request.amount(), "KES");
        Money entryBalance = entry.balance();

        // ── 5. Validate payment amount ────────────────────────────────────────
        // Allow slight overpayment (up to 1 KES) to handle rounding at the till
        Money maxAllowed = entryBalance.add(Money.of(
                java.math.BigDecimal.ONE, "KES"));
        if (payment.isGreaterThan(maxAllowed)) {
            throw new IllegalArgumentException(
                    "Payment of %s exceeds outstanding balance of %s"
                            .formatted(payment, entryBalance));
        }

        // ── 6. Record the payment on the entry ────────────────────────────────
        entry.recordPayment(
                payment,
                request.paymentMethod(),
                request.reference(),
                recordedByUserId
        );
        ContributionEntry saved = entryRepository.save(entry);

        // ── 7. Credit member savings balance ──────────────────────────────────
        member.creditSavings(payment);

        // If member has arrears and this payment exceeds current cycle balance,
        // the excess rolls into arrears clearance
        if (member.hasArrears() && payment.isGreaterThan(entryBalance)) {
            Money excess = payment.subtract(entryBalance);
            member.clearArrears(excess);
            log.info("Arrears cleared: member={} amount={}", member.getId(), excess);
        }
        memberRepository.save(member);

        // ── 8. Update cycle running total ─────────────────────────────────────
        cycle.recordContribution(payment);
        cycleRepository.save(cycle);

        log.info("Manual contribution recorded: member={} cycle={} amount={} method={} by={}",
                member.getId(), cycle.getCycleNumber(), payment,
                request.paymentMethod(), recordedByUserId);

        return toResponse(saved, member.getMemberNumber());
    }

    private ContributionEntryResponse toResponse(ContributionEntry e, String memberNumber) {
        return new ContributionEntryResponse(
                e.getId(), e.getCycleId(), e.getMemberId(),
                null,   // memberName resolved in controller layer if needed
                memberNumber,
                e.getExpectedAmount().getAmount(),
                e.getPaidAmount().getAmount(),
                e.balance().getAmount(),
                e.getArrearsCarriedForward() != null
                        ? e.getArrearsCarriedForward().getAmount()
                        : java.math.BigDecimal.ZERO,
                e.getStatus(),
                e.getLastPaymentMethod(),
                e.getLastMpesaReference(),
                e.getFirstPaymentAt(),
                e.getFullyPaidAt()
        );
    }
}
