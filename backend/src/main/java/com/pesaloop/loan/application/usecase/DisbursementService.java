package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.DisbursementPort;

import com.pesaloop.loan.application.port.out.DisbursementRepository;
import com.pesaloop.loan.application.port.out.DisbursementRepository.DisbursementRecord;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.payment.application.port.out.PaymentRecordRepository;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages disbursement instructions.
 *
 * PesaLoop never initiates money transfers. This service generates
 * instructions that the treasurer executes manually from the group's
 * own M-Pesa, then confirms back with a reference code.
 *
 * All persistence via domain ports — no JDBC or JPA here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementService implements DisbursementPort {

    private final DisbursementRepository disbursementRepo;
    private final LoanAccountRepository loanRepository;
    private final PaymentRecordRepository paymentRecords;

    // ── Generate ──────────────────────────────────────────────────────────────

    @Transactional
    public UUID generateInstruction(
            UUID groupId, UUID loanId, String instructionType,
            UUID recipientMemberId, String recipientName,
            String recipientPhone, BigDecimal amountKes,
            String sourceReference, UUID issuedByUserId) {

        int pending = disbursementRepo.countPendingByGroup(groupId);
        int maxPending = disbursementRepo.getConfig(groupId, "max_pending_disbursements_per_group", 10);
        if (pending >= maxPending) {
            throw new IllegalStateException(
                    "Cannot create disbursement instruction — %d instructions are already pending. "
                    .formatted(pending) +
                    "Confirm or cancel existing instructions before creating new ones.");
        }

        int expiryHours = disbursementRepo.getConfig(groupId, "disbursement_instruction_expiry_hours", 72);
        UUID id = UUID.randomUUID();

        String suggestedRef = loanId != null
                ? disbursementRepo.findLoanReference(loanId).orElse(sourceReference)
                : sourceReference;

        Instant expiresAt = Instant.now().plusSeconds(expiryHours * 3600L);
        disbursementRepo.create(id, groupId, instructionType, loanId, sourceReference,
                recipientMemberId, recipientName, recipientPhone,
                suggestedRef, amountKes, expiresAt, issuedByUserId);

        log.info("Disbursement instruction created: id={} type={} member={} amount={} expires={}h",
                id, instructionType, recipientName, amountKes, expiryHours);
        return id;
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Transactional
    public DisbursementConfirmationResult confirmDisbursement(
            UUID instructionId, String externalMpesaRef,
            String confirmationNotes, UUID confirmedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        DisbursementRecord rec = disbursementRepo.findPendingById(instructionId, groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Disbursement instruction not found: " + instructionId));

        if (!"PENDING".equals(rec.status())) {
            throw new IllegalStateException(
                    "Instruction is " + rec.status() + " — only PENDING instructions can be confirmed");
        }
        if (rec.expiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException(
                    "Instruction expired at " + rec.expiresAt() + ". Re-issue from the loan/payout screen.");
        }
        if (externalMpesaRef != null && disbursementRepo.mpesaRefAlreadyUsed(externalMpesaRef)) {
            throw new IllegalStateException(
                    "M-Pesa reference " + externalMpesaRef + " has already been used to confirm a disbursement");
        }

        disbursementRepo.confirm(instructionId, confirmedByUserId, externalMpesaRef, confirmationNotes);

        String effect = applyDownstreamEffect(rec, externalMpesaRef, confirmedByUserId);

        log.info("Disbursement confirmed: id={} type={} member={} amount={} mpesaRef={} by={}",
                instructionId, rec.instructionType(), rec.recipientName(),
                rec.amountKes(), externalMpesaRef, confirmedByUserId);

        return new DisbursementConfirmationResult(
                instructionId, rec.instructionType(),
                rec.recipientName(), rec.recipientPhone(),
                rec.amountKes(), externalMpesaRef,
                "CONFIRMED", effect);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public void cancelInstruction(UUID instructionId, String reason, UUID cancelledByUserId) {
        UUID groupId = TenantContext.getGroupId();
        // cancel() also reverts loan to APPROVED
        disbursementRepo.cancel(instructionId, reason, cancelledByUserId);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public List<DisbursementInstruction> getPendingInstructions(UUID groupId) {
        return disbursementRepo.findPendingByGroup(groupId).stream()
                .map(r -> new DisbursementInstruction(
                        r.id(), r.groupId(), r.instructionType(), r.loanId(),
                        r.sourceReference(), r.recipientMemberId(), r.recipientName(),
                        r.recipientPhone(), r.suggestedAccountReference(),
                        r.amountKes(), r.status(), r.expiresAt(), r.issuedAt()))
                .toList();
    }

    // ── Downstream effects ────────────────────────────────────────────────────

    private String applyDownstreamEffect(DisbursementRecord rec,
                                          String mpesaRef, UUID confirmedBy) {
        return switch (rec.instructionType()) {
            case "LOAN_DISBURSEMENT" -> activateLoan(rec, mpesaRef, confirmedBy);
            case "MGR_PAYOUT"        -> confirmMgrPayout(rec, mpesaRef);
            case "DIVIDEND_PAYMENT"  -> confirmDividend(rec, mpesaRef);
            default -> "Recorded";
        };
    }

    private String activateLoan(DisbursementRecord rec, String mpesaRef, UUID confirmedBy) {
        if (rec.loanId() == null) return "No loan linked";
        // All loan state changes go through LoanAccountRepository port
        loanRepository.activateAfterDisbursement(rec.loanId(), mpesaRef, confirmedBy);
        paymentRecords.auditLog(rec.groupId(), confirmedBy, "LoanAccount", rec.loanId(),
                "LOAN_DISBURSED_CONFIRMED",
                "{\"mpesaRef\":\"" + mpesaRef + "\",\"confirmedBy\":\"" + confirmedBy + "\"}");
        log.info("Loan activated after disbursement confirmation: loanId={}", rec.loanId());
        return "Loan activated — repayment schedule is now running";
    }

    private String confirmMgrPayout(DisbursementRecord rec, String mpesaRef) {
        paymentRecords.confirmMgrPayout(rec.groupId(), rec.recipientMemberId(), mpesaRef);
        return "MGR payout confirmed for " + rec.recipientName();
    }

    private String confirmDividend(DisbursementRecord rec, String mpesaRef) {
        paymentRecords.confirmDividend(rec.groupId(), rec.recipientMemberId(), mpesaRef);
        return "Dividend confirmed for " + rec.recipientName();
    }

    // ── Domain records ────────────────────────────────────────────────────────

    public record DisbursementInstruction(
            UUID id, UUID groupId, String instructionType, UUID loanId,
            String sourceReference, UUID recipientMemberId,
            String recipientName, String recipientPhone,
            String suggestedAccountReference, BigDecimal amountKes,
            String status, Instant expiresAt, Instant issuedAt
    ) {}

    public record DisbursementConfirmationResult(
            UUID instructionId, String instructionType,
            String recipientName, String recipientPhone,
            BigDecimal amountKes, String mpesaRef,
            String status, String effect
    ) {}
}
