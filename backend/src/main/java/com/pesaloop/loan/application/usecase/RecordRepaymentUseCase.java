package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.RecordRepaymentPort;

import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.payment.application.port.out.PaymentRecordRepository;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records a manual loan repayment.
 * All persistence via domain ports — no JDBC.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordRepaymentUseCase implements RecordRepaymentPort {

    private final LoanAccountRepository loanRepository;
    private final PaymentRecordRepository paymentRecords;

    @Transactional
    public RepaymentResponse execute(RecordRepaymentRequest request, UUID recordedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        LoanAccount loan = loanRepository.findById(request.loanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + request.loanId()));

        if (!groupId.equals(loan.getGroupId()))
            throw new IllegalStateException("Access denied");

        if (loan.getStatus() != LoanStatus.ACTIVE)
            throw new IllegalStateException(
                    "Cannot record repayment — loan status is %s. Only ACTIVE loans accept repayments."
                            .formatted(loan.getStatus()));

        Money payment = Money.of(request.amount(), "KES");
        if (payment.isGreaterThan(loan.totalOutstanding()))
            throw new IllegalArgumentException(
                    "Payment of %s exceeds total outstanding of %s"
                            .formatted(payment, loan.totalOutstanding()));

        // Domain model applies the allocation
        LoanAccount.RepaymentAllocation allocation =
                loan.applyRepayment(payment, request.mpesaReference());

        // Persist updated balances via port
        paymentRecords.updateLoanAfterRepayment(
                loan.getId(),
                loan.getPrincipalBalance().getAmount(),
                loan.getAccruedInterest().getAmount(),
                loan.getPenaltyBalance().getAmount(),
                loan.getTotalPrincipalRepaid().getAmount(),
                loan.getTotalInterestRepaid().getAmount(),
                loan.getStatus().name());

        // Update installment via port
        paymentRecords.applyRepaymentToInstallment(
                loan.getId(),
                allocation.toPrincipal().getAmount(),
                allocation.toInterest().getAmount(),
                allocation.toPenalty().getAmount());

        // Record payment via port
        paymentRecords.recordLoanRepayment(
                groupId, loan.getId(), loan.getMemberId(),
                payment.getAmount(), request.paymentMethod(),
                request.mpesaReference(), request.notes(), recordedByUserId);

        // Release guarantors if settled via port
        if (loan.isSettled()) {
            paymentRecords.releaseGuarantors(loan.getId());
            log.info("Loan settled — guarantors released: loan={}", loan.getId());
        }

        // Audit log via port
        paymentRecords.auditLog(groupId, recordedByUserId, "LoanAccount", loan.getId(),
                "REPAYMENT_RECORDED",
                "{\"payment\":" + payment.getAmount() +
                ",\"toPrincipal\":" + allocation.toPrincipal().getAmount() +
                ",\"toInterest\":" + allocation.toInterest().getAmount() +
                ",\"status\":\"" + loan.getStatus().name() + "\"}");

        log.info("Repayment recorded: loan={} amount={} outstanding={}",
                loan.getId(), payment, loan.totalOutstanding());

        return new RepaymentResponse(
                loan.getId(), loan.getLoanReference(),
                allocation.totalPayment().getAmount(),
                allocation.toPenalty().getAmount(),
                allocation.toInterest().getAmount(),
                allocation.toPrincipal().getAmount(),
                loan.totalOutstanding().getAmount(),
                loan.getStatus());
    }
}
