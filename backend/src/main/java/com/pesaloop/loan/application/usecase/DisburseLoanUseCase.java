package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.DisburseLoanPort;

import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Issues a disbursement instruction for an approved loan.
 *
 * PesaLoop never moves money. This use case generates an instruction
 * record for the treasurer, then sets the loan to PENDING_DISBURSEMENT.
 *
 * All persistence via ports — no JDBC or JPA imports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisburseLoanUseCase implements DisburseLoanPort {

    private final LoanAccountRepository loanRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final DisbursementService disbursementService;

    @Transactional
    public DisburseResponse execute(DisburseLoanRequest request, UUID disbursedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        var loan = loanRepository.findById(request.loanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + request.loanId()));

        if (!groupId.equals(loan.getGroupId()))
            throw new IllegalStateException("Access denied");

        if (loan.getStatus() == LoanStatus.PENDING_DISBURSEMENT)
            throw new IllegalStateException(
                    "Disbursement instruction already issued for " + loan.getLoanReference()
                    + ". Check the pending disbursements queue.");

        if (loan.getStatus() == LoanStatus.ACTIVE)
            throw new IllegalStateException(loan.getLoanReference() + " is already active.");

        if (loan.getStatus() != LoanStatus.APPROVED)
            throw new IllegalStateException(
                    "Loan must be APPROVED. Current status: " + loan.getStatus());

        // Resolve member phone — from request override or member record
        String phone = request.disbursementPhone() != null
                ? request.disbursementPhone()
                : memberRepository.findById(loan.getMemberId())
                        .map(m -> m.getPhoneNumber())
                        .orElseThrow(() -> new IllegalStateException("Member not found"));

        if (phone == null)
            throw new IllegalStateException(
                    "No phone number on file. Update member profile or provide disbursementPhone.");

        // Member name via port — no raw SQL here
        String memberName = loanRepository.findMemberFullName(loan.getMemberId())
                .orElse("Unknown Member");

        // Mark loan — via port, not raw SQL
        loanRepository.markPendingDisbursement(loan.getId());

        // Create disbursement instruction
        UUID instructionId = disbursementService.generateInstruction(
                groupId, loan.getId(), "LOAN_DISBURSEMENT",
                loan.getMemberId(), memberName, phone,
                loan.getPrincipalAmount().getAmount(),
                loan.getLoanReference(), disbursedByUserId);

        log.info("Disbursement instruction issued: loan={} ref={} amount={} member={} instruction={}",
                loan.getId(), loan.getLoanReference(),
                loan.getPrincipalAmount(), memberName, instructionId);

        return new DisburseResponse(
                loan.getId(), loan.getLoanReference(),
                loan.getPrincipalAmount().getAmount(),
                phone, instructionId.toString(),
                LoanStatus.PENDING_DISBURSEMENT);
    }
}
