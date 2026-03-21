package com.pesaloop.payment.application.usecase;

import com.pesaloop.payment.application.port.in.ProcessC2bPaymentPort;
import com.pesaloop.payment.application.port.out.C2bPaymentRepository;
import com.pesaloop.payment.application.port.out.MemberResolutionRepository;
import com.pesaloop.payment.application.port.out.MemberResolutionRepository.GroupRef;
import com.pesaloop.payment.application.port.out.MemberResolutionRepository.MemberRef;
import com.pesaloop.payment.domain.model.PaymentEntryMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processes confirmed C2B (direct paybill) payments from M-Pesa.
 * All persistence delegated to output port interfaces — no SQL here.
 *
 * Member resolution strategy cascade:
 *   1. Exact member number (e.g. "M-017")
 *   2. Member number without prefix (e.g. "017" → "M-017")
 *   3. Member number without dash (e.g. "M017" → "M-017")
 *   4. Member phone number fallback
 *   5. Partial name match (last resort — flagged for manual review)
 *
 * Unmatched payments are saved to unmatched_payments for admin review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C2bPaymentUseCase implements ProcessC2bPaymentPort {

    private final MemberResolutionRepository memberResolution;
    private final C2bPaymentRepository c2bRepository;

    @Override
    @Transactional
    public void process(String transactionId, BigDecimal amount, String phone,
                        String billRefNumber, String shortCode,
                        String firstName, String lastName) {

        // Sanitize bill reference (whatever the member typed as account number)
        String safeRef = billRefNumber != null
                ? billRefNumber.replaceAll("[^a-zA-Z0-9\\-_ ]", "").trim()
                : null;
        if (safeRef != null && safeRef.length() > 50) safeRef = safeRef.substring(0, 50);

        // Idempotency — M-Pesa retries on timeout
        if (c2bRepository.alreadyProcessed(transactionId)) {
            log.info("C2B already processed: transId={} — skipping", transactionId);
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("C2B invalid amount: transId={} amount={}", transactionId, amount);
            c2bRepository.saveUnmatched(transactionId, amount, phone, safeRef, shortCode);
            return;
        }

        // Resolve group by shortcode
        GroupRef group = memberResolution.findGroupByShortcode(shortCode).orElse(null);
        if (group == null) {
            log.warn("C2B no group found for shortcode={}", shortCode);
            c2bRepository.saveUnmatched(transactionId, amount, phone, safeRef, shortCode);
            return;
        }

        // Resolve member by account reference
        MemberRef member = resolveMember(group.groupId(), phone, safeRef);
        if (member == null) {
            log.warn("C2B unmatched: shortcode={} ref={} phone={} transId={}",
                    shortCode, safeRef, phone, transactionId);
            c2bRepository.saveUnmatched(transactionId, amount, phone, safeRef, shortCode);
            return;
        }

        // Find open contribution entry for this member (if any)
        UUID entryId = c2bRepository.findOpenEntryId(group.groupId(), member.memberId())
                .orElse(null);

        // Apply payment
        c2bRepository.applyC2bPayment(group.groupId(), member.memberId(), entryId,
                amount, transactionId, phone, PaymentEntryMethod.C2B_PAYBILL.name());

        log.info("C2B payment processed: group={} member={} memberNo={} amount={} transId={}",
                group.groupId(), member.memberId(), member.memberNumber(), amount, transactionId);
    }

    // ── Member resolution — cascades through 5 strategies ────────────────────

    private MemberRef resolveMember(UUID groupId, String phone, String billRef) {
        if (billRef == null || billRef.isBlank()) {
            return resolveByPhone(groupId, phone);
        }

        String cleaned = billRef.trim().toUpperCase();

        // Strategy 1: exact member number (e.g. "M-017")
        MemberRef m = memberResolution.findByMemberNumber(groupId, cleaned).orElse(null);
        if (m != null) return m;

        // Strategy 2: numeric only → prefix with "M-" (e.g. "017" → "M-017")
        if (cleaned.matches("\\d+")) {
            m = memberResolution.findByMemberNumber(groupId, "M-" + cleaned).orElse(null);
            if (m != null) return m;
        }

        // Strategy 3: M without dash (e.g. "M017" → "M-017")
        if (cleaned.startsWith("M") && !cleaned.startsWith("M-")) {
            m = memberResolution.findByMemberNumber(groupId, "M-" + cleaned.substring(1)).orElse(null);
            if (m != null) return m;
        }

        // Strategy 4: phone number fallback
        m = resolveByPhone(groupId, phone);
        if (m != null) return m;

        // Strategy 5: partial name match (last resort)
        return memberResolution.findByNamePartial(groupId, billRef.trim()).orElse(null);
    }

    private MemberRef resolveByPhone(UUID groupId, String phone) {
        if (phone == null) return null;
        MemberRef m = memberResolution.findByPhone(groupId, phone).orElse(null);
        if (m != null) return m;
        return memberResolution.findByUserPhone(groupId, phone).orElse(null);
    }
}
