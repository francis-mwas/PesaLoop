package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.InitiateStkPushPort;

import com.pesaloop.contribution.application.dto.ContributionDtos.*;
import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.contribution.application.port.out.ContributionCycleRepository;
import com.pesaloop.contribution.application.port.out.ContributionEntryRepository;
import com.pesaloop.group.domain.model.Group;
import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.contribution.application.port.out.StkPushRepository;
import com.pesaloop.payment.application.port.out.MpesaGateway;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Initiates an M-Pesa STK Push to collect a contribution from a member.
 *
 * Flow:
 *   1. Validate cycle is open and member is active
 *   2. Determine amount to collect (full balance or requested partial)
 *   3. Persist a stk_push_requests record in PENDING state BEFORE calling M-Pesa
 *      → If M-Pesa call fails, we have a record to retry or expire
 *      → If the callback arrives before we commit, the webhook handler waits briefly
 *        (the PENDING record acts as a distributed lock key)
 *   4. Call Daraja STK Push API
 *   5. Update the stk_push_requests record with the M-Pesa checkoutRequestId
 *   6. Return the checkout details to the caller — frontend can poll for status
 *
 * The actual contribution credit happens in MpesaWebhookService when the callback
 * arrives confirming the member paid. This use case only initiates the request.
 *
 * Rate limiting: max 1 pending STK push per member per cycle at a time.
 * If a push is already pending and not expired, we return it rather than creating a new one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitiateStkPushUseCase implements InitiateStkPushPort {

    private final ContributionCycleRepository cycleRepository;
    private final ContributionEntryRepository entryRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final MpesaGateway mpesaGateway;
    private final StkPushRepository stkPushRepository;

    @Transactional
    public StkPushResponse execute(InitiateStkPushRequest request, UUID initiatedBy) {

        UUID groupId = TenantContext.getGroupId();

        // ── 1. Load cycle, group, member, entry ───────────────────────────────
        ContributionCycle cycle = cycleRepository.findById(request.cycleId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cycle not found: " + request.cycleId()));

        if (!cycle.isOpen()) {
            throw new IllegalStateException(
                    "Cycle %d is not open for contributions (status: %s)"
                            .formatted(cycle.getCycleNumber(), cycle.getStatus()));
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        if (group.getMpesaShortcode() == null) {
            throw new IllegalStateException(
                    "Group has no M-Pesa shortcode configured. Set up M-Pesa integration first.");
        }

        Member member = memberRepository.findById(request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Member not found: " + request.memberId()));

        if (!member.isActive()) {
            throw new IllegalStateException(
                    "Member is not active (status: " + member.getStatus() + ")");
        }

        ContributionEntry entry = entryRepository
                .findByCycleIdAndMemberId(request.cycleId(), request.memberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No contribution entry for member in this cycle"));

        if (entry.isFullyPaid()) {
            throw new IllegalStateException(
                    "Member has already fully paid for cycle " + cycle.getCycleNumber());
        }

        // ── 2. Determine phone number ─────────────────────────────────────────
        String phone = request.phoneNumber() != null
                ? request.phoneNumber()
                : member.getPhoneNumber();

        if (phone == null) {
            throw new IllegalStateException(
                    "No phone number available for member. Provide a phone number or update member profile.");
        }

        // ── 3. Determine collection amount ────────────────────────────────────
        // Collect the full outstanding balance unless a specific amount was requested
        Money balance = entry.balance();
        Money toCollect;
        if (request.amount() != null) {
            toCollect = Money.of(request.amount(), "KES");
            if (toCollect.isGreaterThan(balance)) {
                throw new IllegalArgumentException(
                        "Requested amount %s exceeds outstanding balance %s"
                                .formatted(toCollect, balance));
            }
        } else {
            toCollect = balance;
        }

        // M-Pesa only handles whole shillings — round up to nearest shilling
        long amountKes = toCollect.getAmount()
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();

        if (amountKes < 1) {
            throw new IllegalStateException("Collection amount must be at least KES 1");
        }

        // ── 4. Check for existing unexpired pending push ──────────────────────
        String existingCheckoutId = findPendingStkPush(request.memberId(), request.cycleId());
        if (existingCheckoutId != null) {
            log.info("Returning existing pending STK push for member={} cycle={}",
                    request.memberId(), request.cycleId());
            return new StkPushResponse(
                    existingCheckoutId, null,
                    "A payment request is already pending. Please check your phone.",
                    member.getId(), toCollect.getAmount(), phone);
        }

        // ── 5. Persist STK push record BEFORE calling M-Pesa ─────────────────
        UUID stkRequestId = UUID.randomUUID();
        String accountRef = buildAccountReference(member.getMemberNumber(), cycle.getCycleNumber(),
                cycle.getYear());

        stkPushRepository.create(stkRequestId, groupId, member.getId(), entry.getId(), phone, BigDecimal.valueOf(amountKes));

        // ── 6. Call M-Pesa Daraja ─────────────────────────────────────────────
        MpesaGateway.StkPushResult mpesaResponse;
        try {
            mpesaResponse = mpesaGateway.initiateStkPush(
                    group.getMpesaShortcode(),
                    phone,
                    amountKes,
                    accountRef,
                    stkRequestId.toString()
            );
        } catch (Exception e) {
            // Mark the request as failed so it doesn't block future pushes
            stkPushRepository.markFailed(stkRequestId, e.getMessage());
            throw new IllegalStateException("M-Pesa STK Push failed: " + e.getMessage(), e);
        }

        if (!mpesaResponse.isSuccess()) {
            stkPushRepository.markFailed(stkRequestId, mpesaResponse.responseDescription());
            throw new IllegalStateException(
                    "M-Pesa rejected the STK push: " + mpesaResponse.responseDescription());
        }

        // ── 7. Update record with M-Pesa's IDs ───────────────────────────────
        stkPushRepository.updateWithMpesaIds(stkRequestId, mpesaResponse.merchantRequestId(), mpesaResponse.checkoutRequestId());

        log.info("STK Push initiated: member={} amount={} KES phone={} checkoutId={}",
                member.getId(), amountKes, phone, mpesaResponse.checkoutRequestId());

        return new StkPushResponse(
                mpesaResponse.checkoutRequestId(),
                mpesaResponse.merchantRequestId(),
                mpesaResponse.customerMessage(),
                member.getId(),
                BigDecimal.valueOf(amountKes),
                phone
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String findPendingStkPush(UUID memberId, UUID cycleId) {
        return stkPushRepository.findPendingCheckoutIdByCycle(memberId, cycleId).orElse(null);
    }

    /**
     * Builds the account reference shown on the member's phone during payment.
     * E.g. "M-017 | Cycle 4 | 2024"
     */
    private String buildAccountReference(String memberNumber, int cycleNumber, int year) {
        String ref = "%s | Cycle %d | %d".formatted(memberNumber, cycleNumber, year);
        // M-Pesa account reference max length is 20 characters
        return ref.length() > 20 ? memberNumber.replaceAll("[^A-Z0-9]", "") : ref;
    }
}
