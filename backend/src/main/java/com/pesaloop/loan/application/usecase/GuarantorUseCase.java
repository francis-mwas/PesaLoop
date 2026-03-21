package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.GuarantorPort;

import com.pesaloop.loan.application.port.out.GuarantorRepository;
import com.pesaloop.loan.application.port.out.GuarantorRepository.GuarantorRecord;
import com.pesaloop.notification.application.usecase.SmsService;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Guarantor workflow for loans that require guarantors before approval.
 *
 * Flow:
 *   1. Admin nominates guarantors → SMS sent to each guarantor
 *   2. Each guarantor accepts or declines via the app
 *      → All accepted → loan auto-advances to PENDING_APPROVAL → admin notified
 *      → Any declines → admin notified, must nominate replacement
 *
 * All persistence via GuarantorRepository port — no JDBC here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuarantorUseCase implements GuarantorPort {

    private final GuarantorRepository guarantorRepo;
    private final SmsService smsService;

    // ── 1. Admin nominates guarantors ─────────────────────────────────────────

    @Transactional
    public void nominateGuarantors(UUID loanId, List<UUID> guarantorMemberIds,
                                    UUID nominatedByUserId) {
        UUID groupId = TenantContext.getGroupId();

        String status = guarantorRepo.findLoanStatus(loanId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));

        if (!"PENDING_GUARANTOR".equals(status))
            throw new IllegalStateException("Loan is not awaiting guarantors. Current status: " + status);

        UUID applicantMemberId = guarantorRepo.findApplicantMemberId(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan applicant not found"));

        if (guarantorMemberIds.contains(applicantMemberId))
            throw new IllegalArgumentException("A member cannot be their own guarantor.");

        String loanRef    = guarantorRepo.findLoanReference(loanId).orElse(loanId.toString());
        String applicant  = guarantorRepo.findMemberFullName(applicantMemberId).orElse("the applicant");
        String groupName  = guarantorRepo.findGroupName(groupId).orElse("your group");

        for (UUID guarantorMemberId : guarantorMemberIds) {
            guarantorRepo.create(loanId, groupId, guarantorMemberId);

            String phone = guarantorRepo.findMemberPhone(guarantorMemberId).orElse(null);
            String name  = guarantorRepo.findMemberFullName(guarantorMemberId).orElse("Member");
            if (phone != null) {
                smsService.send(phone,
                        String.format("PesaLoop: %s, you have been nominated as guarantor for " +
                                "loan %s by %s in %s. Open the PesaLoop app to accept or decline.",
                                name, loanRef, applicant, groupName),
                        groupId, guarantorMemberId);
            }
        }
        log.info("Guarantors nominated: loan={} count={} by={}", loanId, guarantorMemberIds.size(), nominatedByUserId);
    }

    // ── 2. Guarantor responds ─────────────────────────────────────────────────

    @Transactional
    public GuarantorResponse respond(UUID loanId, UUID guarantorMemberId,
                                      boolean accepted, String note) {
        UUID groupId = TenantContext.getGroupId();

        int updated = guarantorRepo.respond(loanId, guarantorMemberId, accepted, note);
        if (updated == 0)
            throw new IllegalArgumentException("Guarantor record not found or already responded.");

        String loanRef = guarantorRepo.findLoanReference(loanId).orElse(loanId.toString());

        if (!accepted) {
            String adminPhone = guarantorRepo.findAdminPhone(groupId).orElse(null);
            String guarantorName = guarantorRepo.findMemberFullName(guarantorMemberId).orElse("A guarantor");
            if (adminPhone != null) {
                smsService.send(adminPhone,
                        String.format("PesaLoop: %s declined to guarantee loan %s. Reason: %s. Please nominate another.",
                                guarantorName, loanRef, note != null ? note : "none given"),
                        groupId, null);
            }
            log.info("Guarantor declined: loan={} guarantor={}", loanId, guarantorMemberId);
            return new GuarantorResponse(loanId, "PENDING_GUARANTOR",
                    "Guarantor declined. Admin has been notified. Please nominate another.");
        }

        int acceptedCount = guarantorRepo.countAccepted(loanId);
        int required = guarantorRepo.findRequiredGuarantorCount(loanId);

        if (acceptedCount >= required) {
            guarantorRepo.advanceToApproval(loanId);
            String adminPhone = guarantorRepo.findAdminPhone(groupId).orElse(null);
            if (adminPhone != null) {
                smsService.send(adminPhone,
                        "PesaLoop: All guarantors accepted loan " + loanRef + ". Ready for your approval.",
                        groupId, null);
            }
            log.info("All guarantors accepted — loan advanced to PENDING_APPROVAL: loan={}", loanId);
            return new GuarantorResponse(loanId, "PENDING_APPROVAL",
                    "All guarantors accepted. Loan " + loanRef + " is ready for admin approval.");
        }

        log.info("Guarantor accepted ({}/{}): loan={}", accepted, required, loanId);
        return new GuarantorResponse(loanId, "PENDING_GUARANTOR",
                String.format("Accepted (%d of %d guarantors confirmed).", acceptedCount, required));
    }

    // ── 3. View guarantors ────────────────────────────────────────────────────

    public List<GuarantorRecord> getGuarantors(UUID loanId) {
        UUID groupId = TenantContext.getGroupId();
        return guarantorRepo.findByLoanId(loanId, groupId);
    }

    public record GuarantorResponse(UUID loanId, String loanStatus, String message) {}
}
