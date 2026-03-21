package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.ApproveLoanPort;

import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.loan.application.port.out.LoanAccountRepository.LoanDetail;
import com.pesaloop.loan.application.port.out.LoanProductRepository;
import com.pesaloop.loan.application.port.out.RepaymentInstallmentRepository;
import com.pesaloop.loan.domain.service.InterestCalculationService;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Approves or rejects a loan application.
 *
 * Dependencies are all domain ports — no JDBC, no JPA.
 * The adapter (LoanAccountJdbcAdapter) owns the SQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApproveLoanUseCase implements ApproveLoanPort {

    private final LoanAccountRepository loanRepository;
    private final LoanProductRepository productRepository;
    private final GroupRepository groupRepository;
    private final RepaymentInstallmentRepository installmentRepository;
    private final InterestCalculationService interestService;

    @Transactional
    public LoanDetailResponse execute(ProcessLoanRequest request, UUID approvedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        LoanDetail loan = loanRepository.findDetailById(request.loanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + request.loanId()));

        if (!LoanStatus.PENDING_APPROVAL.name().equals(loan.status())) {
            throw new IllegalStateException(
                    "Loan must be PENDING_APPROVAL to process. Current status: " + loan.status());
        }

        // ── Reject ────────────────────────────────────────────────────────────
        if (ProcessLoanRequest.LoanDecision.REJECT == request.decision()) {
            loanRepository.reject(request.loanId(), request.note(), approvedByUserId);
            log.info("Loan rejected: loan={} by={} reason={}", request.loanId(), approvedByUserId, request.note());
            return buildResponse(request.loanId());
        }

        // ── Approve ───────────────────────────────────────────────────────────
        LoanProduct product = productRepository.findById(loan.productId())
                .orElseThrow(() -> new IllegalStateException("Loan product not found"));

        // Admin may reduce amount but never increase it
        Money requestedAmount = loan.principalAmount();
        Money approvedAmount  = request.approvedAmount() != null
                ? Money.ofKes(BigDecimal.valueOf(request.approvedAmount()))
                : requestedAmount;

        if (approvedAmount.isGreaterThan(requestedAmount)) {
            throw new IllegalArgumentException(
                    "Approved amount cannot exceed requested amount of " + requestedAmount);
        }

        int periods   = request.approvedPeriods() != null ? request.approvedPeriods() : product.getDefaultRepaymentPeriods();
        Money interest = interestService.calculateInterest(approvedAmount, product, periods);
        LocalDate dueDate = LocalDate.now().plusMonths(periods);

        loanRepository.approve(request.loanId(), approvedAmount, interest, dueDate, approvedByUserId, request.note());

        // Generate repayment schedule
        var schedule = interestService.generateSchedule(
                request.loanId(), groupId, approvedAmount, interest, product, periods, LocalDate.now());
        schedule.forEach(installmentRepository::save);

        log.info("Loan approved: loan={} amount={} periods={} dueDate={} by={}",
                request.loanId(), approvedAmount, periods, dueDate, approvedByUserId);

        return buildResponse(request.loanId());
    }

    private LoanDetailResponse buildResponse(UUID loanId) {
        LoanDetail d = loanRepository.findDetailById(loanId)
                .orElseThrow(() -> new IllegalStateException("Loan not found after processing"));

        List<InstallmentResponse> installments = installmentRepository.findByLoanId(loanId)
                .stream()
                .map(i -> new InstallmentResponse(
                        i.getInstallmentNumber(),
                        i.getDueDate(),
                        i.getPrincipalDue() != null ? i.getPrincipalDue().getAmount() : java.math.BigDecimal.ZERO,
                        i.getInterestDue() != null ? i.getInterestDue().getAmount() : java.math.BigDecimal.ZERO,
                        i.getTotalDue() != null ? i.getTotalDue().getAmount() : java.math.BigDecimal.ZERO,
                        i.getBalanceAfter() != null ? i.getBalanceAfter().getAmount() : null,
                        i.getPrincipalPaid() != null ? i.getPrincipalPaid().getAmount() : java.math.BigDecimal.ZERO,
                        i.getInterestPaid() != null ? i.getInterestPaid().getAmount() : java.math.BigDecimal.ZERO,
                        i.getPenaltyPaid() != null ? i.getPenaltyPaid().getAmount() : java.math.BigDecimal.ZERO,
                        i.getStatus(),
                        i.getPaidAt()))
                .toList();

        return new LoanDetailResponse(
                d.id(), d.loanReference(), d.memberId(), d.memberName(), d.memberNumber(),
                d.productName(), LoanStatus.valueOf(d.status()),
                d.principalAmount().getAmount(), d.totalInterestCharged().getAmount(),
                d.principalBalance().getAmount(), d.accruedInterest().getAmount(),
                d.penaltyBalance().getAmount(),
                d.principalAmount().add(d.totalInterestCharged()).getAmount(),
                d.totalPrincipalRepaid().getAmount(), d.totalInterestRepaid().getAmount(),
                d.disbursementDate(), d.dueDate(), d.disbursementMpesaRef(),
                installments
        );
    }
}
