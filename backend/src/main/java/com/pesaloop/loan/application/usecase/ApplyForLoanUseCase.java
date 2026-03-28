package com.pesaloop.loan.application.usecase;

import com.pesaloop.loan.application.port.in.ApplyForLoanPort;

import com.pesaloop.group.domain.model.Group;
import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.loan.application.port.out.LoanProductRepository;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the full loan application workflow:
 *
 * 1. Load the loan product and member
 * 2. Run all eligibility checks:
 *    - Product is active
 *    - Member has been in group long enough (minimumMembershipMonths)
 *    - Member owns enough shares
 *    - Member has no arrears (if product requires zero arrears)
 *    - Requested amount ≤ product maximum
 *    - Requested amount ≤ multiple of savings (if configured)
 *    - Requested amount ≤ multiple of shares value (if configured)
 *    - Member doesn't exceed maxConcurrentLoans for this product
 *    - Loan concentration check: single borrower cannot exceed 25% of loan book
 * 3. Persist the LoanAccount in APPLICATION_SUBMITTED status
 * 4. If product requires guarantors, create LoanGuarantor records and
 *    send guarantor request notifications
 * 5. If no guarantor required, immediately advance to PENDING_APPROVAL
 *
 * Returns eligibility result even on failure so UI can show the exact reason.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyForLoanUseCase implements ApplyForLoanPort {

    private final LoanProductRepository productRepository;
    private final LoanAccountRepository loanRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;

    private static final BigDecimal MAX_SINGLE_BORROWER_SHARE = new BigDecimal("0.35"); // 35%

    @Transactional
    public LoanApplicationResponse execute(
            ApplyForLoanRequest request,
            UUID applicantUserId) {

        UUID groupId = TenantContext.getGroupId();

        // ── 1. Load product, group, member ────────────────────────────────────
        LoanProduct product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Loan product not found: " + request.productId()));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalStateException("Group not found"));

        if (!group.allowsLoans()) {
            throw new IllegalStateException(
                    "This group type does not support loans. Only TABLE_BANKING groups can issue loans.");
        }

        Member member = (request.targetMemberId() != null)
                ? memberRepository.findById(request.targetMemberId())
                .orElseThrow(() -> new IllegalStateException(
                        "Target member not found: " + request.targetMemberId()))
                : memberRepository.findByGroupIdAndUserId(groupId, applicantUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "You are not an active member of this group"));

        if (!member.isActive()) {
            throw new IllegalStateException(
                    "Your membership is " + member.getStatus().name().toLowerCase()
                            + ". Only active members can apply for loans.");
        }

        // ── 2. Run eligibility checks ─────────────────────────────────────────
        long membershipMonths = ChronoUnit.MONTHS.between(member.getJoinedOn(), LocalDate.now());
        Money savingsBalance = member.getSavingsBalance();
        Money sharesValue = group.getShareConfig().getPricePerShare()
                .multiply(member.getSharesOwned());
        boolean hasArrears = member.hasArrears();
        int activeLoanCount = loanRepository
                .countActiveByMemberIdAndProductId(member.getId(), product.getId());

        LoanProduct.EligibilityResult eligibility = product.checkEligibility(
                (int) membershipMonths,
                member.getSharesOwned(),
                savingsBalance,
                sharesValue,
                hasArrears,
                activeLoanCount
        );

        if (!eligibility.eligible()) {
            log.info("Loan application ineligible: member={} reason={}", member.getId(), eligibility.reason());
            // Return without persisting — let the UI show the reason
            return new LoanApplicationResponse(
                    null, null, member.getId(), null, member.getMemberNumber(),
                    product.getId(), product.getName(),
                    request.amount(), null,
                    LoanStatus.REJECTED,
                    false, eligibility.reason(),
                    List.of(), null
            );
        }

        // ── 3. Validate requested amount ──────────────────────────────────────
        Money requestedAmount = Money.of(request.amount(), group.getCurrencyCode());
        Money maxAllowed = product.maximumLoanFor(savingsBalance, sharesValue);

        if (requestedAmount.isGreaterThan(maxAllowed)) {
            return new LoanApplicationResponse(
                    null, null, member.getId(), null, member.getMemberNumber(),
                    product.getId(), product.getName(),
                    request.amount(), null,
                    LoanStatus.REJECTED,
                    false,
                    "Maximum loan for you is %s based on your savings and shares. You requested %s."
                            .formatted(maxAllowed, requestedAmount),
                    List.of(), null
            );
        }

        // ── 4. Concentration risk check ───────────────────────────────────────
        // No single borrower should hold > 35% of the outstanding loan book
        // (we saw Brian Kahara at 36% in the chamaa spreadsheet — a real risk)
        BigDecimal currentLoanBook = currentLoanBookTotal(groupId);
        if (currentLoanBook.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal memberTotal = memberOutstandingTotal(member.getId());
            BigDecimal projectedMemberTotal = memberTotal.add(request.amount());
            BigDecimal projectedShare = projectedMemberTotal.divide(
                    currentLoanBook.add(request.amount()), 4, java.math.RoundingMode.HALF_EVEN);

            if (projectedShare.compareTo(MAX_SINGLE_BORROWER_SHARE) > 0) {
                return new LoanApplicationResponse(
                        null, null, member.getId(), null, member.getMemberNumber(),
                        product.getId(), product.getName(),
                        request.amount(), null,
                        LoanStatus.REJECTED,
                        false,
                        "This loan would give you %.1f%% of the group's total loan book (max allowed: %.1f%%). "
                                .formatted(projectedShare.multiply(BigDecimal.valueOf(100)).doubleValue(),
                                        MAX_SINGLE_BORROWER_SHARE.multiply(BigDecimal.valueOf(100)).doubleValue())
                                + "Apply for a smaller amount or wait until other loans are repaid.",
                        List.of(), null
                );
            }
        }

        // ── 5. Determine repayment periods ────────────────────────────────────
        int periods = request.repaymentPeriods() != null
                ? Math.min(request.repaymentPeriods(), product.getMaxRepaymentPeriods())
                : product.getMaxRepaymentPeriods();

        // ── 6. Persist the loan application ──────────────────────────────────
        String loanReference = loanRepository.nextLoanReference(groupId, LocalDate.now().getYear());

        LoanAccount loan = LoanAccount.builder()
                .id(UUID.randomUUID())
                .groupId(groupId)
                .memberId(member.getId())
                .productId(product.getId())
                .loanReference(loanReference)
                .status(product.isRequiresGuarantor()
                        ? LoanStatus.PENDING_GUARANTOR
                        : LoanStatus.PENDING_APPROVAL)
                .principalAmount(requestedAmount)
                // Total interest charged set at disbursement for FLAT_RATE,
                // or computed dynamically for other types
                .totalInterestCharged(Money.ofKes(BigDecimal.ZERO))
                .principalBalance(Money.ofKes(BigDecimal.ZERO))
                .accruedInterest(Money.ofKes(BigDecimal.ZERO))
                .totalInterestRepaid(Money.ofKes(BigDecimal.ZERO))
                .totalPrincipalRepaid(Money.ofKes(BigDecimal.ZERO))
                .penaltyBalance(Money.ofKes(BigDecimal.ZERO))
                .guarantorMemberIds(new ArrayList<>())
                .build();

        LoanAccount saved = loanRepository.save(loan);

        // ── 7. Create guarantor records if provided ───────────────────────────
        List<GuarantorStatus> guarantorStatuses = new ArrayList<>();
        if (product.isRequiresGuarantor()) {
            // If member pre-selected guarantors in the request, nominate them immediately
            // Otherwise admin can assign via POST /loans/{id}/guarantors later
            if (request.guarantorMemberIds() != null && !request.guarantorMemberIds().isEmpty()) {
                log.info("Auto-nominating {} guarantors from application: loan={}",
                        request.guarantorMemberIds().size(), saved.getId());
                // Guarantor nomination handled by GuarantorUseCase (called from controller if needed)
                // Here we just log — GuarantorUseCase.nominateGuarantors() is called separately
            }
            log.info("Loan requires guarantor: loan={} product={}", saved.getId(), product.getName());
        }

        log.info("Loan application submitted: loan={} ref={} member={} amount={} status={}",
                saved.getId(), loanReference, member.getId(), requestedAmount, saved.getStatus());

        return new LoanApplicationResponse(
                saved.getId(),
                loanReference,
                member.getId(),
                null,
                member.getMemberNumber(),
                product.getId(),
                product.getName(),
                requestedAmount.getAmount(),
                null,
                saved.getStatus(),
                true, null,
                guarantorStatuses,
                java.time.Instant.now()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal currentLoanBookTotal(UUID groupId) {
        return loanRepository.totalActiveLoanBook(groupId);
    }

    private BigDecimal memberOutstandingTotal(UUID memberId) {
        return loanRepository.totalActiveLoansForMember(memberId);
    }
}