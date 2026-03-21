package com.pesaloop.payment.application.usecase;

import com.pesaloop.payment.application.port.in.RecordManualPaymentPort;
import com.pesaloop.payment.application.port.out.ManualPaymentRepository;
import com.pesaloop.payment.application.port.out.ManualPaymentRepository.LoanInfo;
import com.pesaloop.payment.application.port.out.ManualPaymentRepository.MemberInfo;
import com.pesaloop.payment.domain.model.PaymentEntryMethod;
import com.pesaloop.shared.domain.TenantContext;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records a manual (non-M-Pesa) contribution payment or loan repayment.
 * All persistence delegated to ManualPaymentRepository output port — no SQL here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordManualPaymentUseCase implements RecordManualPaymentPort {

    private final ManualPaymentRepository paymentRepository;

    public enum PaymentTarget { CONTRIBUTION, LOAN_REPAYMENT }

    public record ManualPaymentRequest(
            @NotNull UUID memberId,
            @NotNull PaymentTarget target,
            UUID cycleId,
            UUID loanId,
            @NotNull @Positive BigDecimal amount,
            @NotNull PaymentEntryMethod method,
            String reference,
            String notes,
            LocalDate paymentDate
    ) {}

    public record ManualPaymentResponse(
            UUID paymentRecordId, UUID memberId, String memberNumber,
            BigDecimal amount, PaymentEntryMethod method, String reference,
            PaymentTarget target, String targetReference, String status
    ) {}

    @Transactional
    public ManualPaymentResponse execute(ManualPaymentRequest req, UUID recordedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        // ── 1. Validate member ────────────────────────────────────────────────
        MemberInfo member = paymentRepository.findActiveMember(req.memberId(), groupId);
        if (member == null)
            throw new IllegalArgumentException("Member not found in this group");
        if (!"ACTIVE".equals(member.status()))
            throw new IllegalStateException("Cannot record payment for member with status: " + member.status());

        // ── 2. Validate method is MANUAL ──────────────────────────────────────
        if (!req.method().name().startsWith("MANUAL_"))
            throw new IllegalArgumentException(
                "Method must be a MANUAL_ type. Got: " + req.method() +
                ". For M-Pesa use the STK Push or Paybill endpoints.");

        String internalRef = (req.reference() != null && !req.reference().isBlank())
                ? req.reference() : "MAN-" + System.currentTimeMillis();

        // ── 3. Apply payment to target ────────────────────────────────────────
        String targetRef;
        UUID resolvedCycleId = req.cycleId();
        UUID resolvedLoanId  = req.loanId();

        if (req.target() == PaymentTarget.CONTRIBUTION) {
            if (resolvedCycleId == null) {
                resolvedCycleId = paymentRepository.findCurrentOpenCycleId(groupId, req.memberId());
                if (resolvedCycleId == null)
                    throw new IllegalStateException(
                        "No open contribution cycle found for this member. Specify a cycleId.");
            }
            int cycleNumber = paymentRepository.validateAndGetCycleNumber(resolvedCycleId, groupId);
            paymentRepository.applyContributionPayment(resolvedCycleId, req.memberId(),
                    req.amount(), req.method().name(), internalRef, recordedByUserId);
            targetRef = "Cycle #" + cycleNumber;

        } else {
            if (resolvedLoanId == null)
                throw new IllegalArgumentException("loanId is required for LOAN_REPAYMENT target");

            LoanInfo loan = paymentRepository.findActiveLoan(resolvedLoanId, req.memberId(), groupId);
            if (loan == null)
                throw new IllegalArgumentException("Loan not found for this member");
            if (!"ACTIVE".equals(loan.status()))
                throw new IllegalStateException("Loan " + loan.loanRef() + " is not active");

            BigDecimal totalOutstanding = loan.principalBalance()
                    .add(loan.accruedInterest()).add(loan.penaltyBalance());
            if (req.amount().compareTo(totalOutstanding) > 0)
                throw new IllegalArgumentException(
                    "Payment of %s exceeds outstanding balance of %s for loan %s"
                        .formatted(req.amount(), totalOutstanding, loan.loanRef()));

            // Allocate: penalty → interest → principal
            BigDecimal remaining  = req.amount();
            BigDecimal toPenalty  = remaining.min(loan.penaltyBalance());
            remaining = remaining.subtract(toPenalty);
            BigDecimal toInterest = remaining.min(loan.accruedInterest());
            remaining = remaining.subtract(toInterest);
            BigDecimal toPrincipal = remaining;
            boolean fullySettled  = req.amount().compareTo(totalOutstanding) >= 0;

            paymentRepository.applyLoanRepayment(resolvedLoanId, toPrincipal, toInterest, toPenalty, fullySettled);
            paymentRepository.applyRepaymentToInstallment(resolvedLoanId, toPrincipal, toInterest, toPenalty);
            if (fullySettled) paymentRepository.releaseGuarantors(resolvedLoanId);
            targetRef = loan.loanRef();
        }

        // ── 4. Record payment + audit ─────────────────────────────────────────
        UUID paymentRecordId = paymentRepository.recordPayment(
                groupId, req.memberId(), resolvedCycleId, resolvedLoanId,
                req.target().name(), req.amount(), req.method().name(),
                internalRef, req.notes(), recordedByUserId);

        log.info("Manual payment recorded: group={} member={} amount={} method={} ref={} by={}",
                groupId, member.memberNumber(), req.amount(), req.method(), internalRef, recordedByUserId);

        return new ManualPaymentResponse(
                paymentRecordId, req.memberId(), member.memberNumber(),
                req.amount(), req.method(), internalRef, req.target(), targetRef, "RECORDED");
    }
}
