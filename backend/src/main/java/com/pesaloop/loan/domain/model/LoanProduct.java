package com.pesaloop.loan.domain.model;

import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A LoanProduct defines the terms under which a group lends money.
 * Each group can have multiple products (e.g. "Emergency Loan", "School Fees Loan").
 *
 * Real-world examples:
 *   - Table banking chamaa: 1 product, 10% flat rate, repay in 3 months
 *   - Larger SACCO: 3 products — Emergency (1 month), Development (12 months), School (6 months)
 *
 * This is a pure domain object — no JPA annotations here.
 */
@Getter
@Builder
public class LoanProduct {

    private final UUID id;
    private final UUID groupId;

    private String name;                    // e.g. "Emergency Loan", "School Fees"
    private String description;

    private boolean active;

    // ── Interest configuration ─────────────────────────────────────────────
    private InterestType interestType;
    private InterestAccrualFrequency accrualFrequency;
    private BigDecimal interestRate;        // e.g. 0.10 = 10% per accrual period
    private Integer customAccrualIntervalDays; // only when accrualFrequency = CUSTOM

    // ── Loan size limits ──────────────────────────────────────────────────
    private Money minimumAmount;
    private Money maximumAmount;

    /**
     * Maximum loan as a multiple of the member's total savings.
     * e.g. 3.0 means you can borrow up to 3× what you've saved.
     * null = no savings-based cap (use maximumAmount only).
     */
    private BigDecimal maxMultipleOfSavings;

    /**
     * Maximum loan as a multiple of the member's shares value.
     * e.g. 2.0 = borrow up to 2× your shares × share price.
     * null = not applicable.
     */
    private BigDecimal maxMultipleOfSharesValue;

    // ── Repayment configuration ───────────────────────────────────────────
    private int maxRepaymentPeriods;        // number of repayment periods
    private InterestAccrualFrequency repaymentFrequency; // how often repayments are made

    /**
     * If true, the full loan + interest must be paid in one lump sum at the end.
     * If false, installment repayments are used (amortization schedule generated).
     */
    private boolean bulletRepayment;

    // ── Eligibility criteria ──────────────────────────────────────────────
    private int minimumMembershipMonths;    // must be a member for this many months
    private int minimumSharesOwned;         // must own at least this many shares
    private boolean requiresGuarantor;
    private int maxGuarantors;

    /**
     * If true, member must have zero arrears to qualify.
     */
    private boolean requiresZeroArrears;

    /**
     * Maximum number of active loans a member can have under THIS product.
     */
    private int maxConcurrentLoans;

    // ── Penalty configuration ─────────────────────────────────────────────
    private BigDecimal lateRepaymentPenaltyRate; // % of missed installment
    private int penaltyGracePeriodDays;


    /** Alias for maxRepaymentPeriods — the default number of periods for new loans. */
    public int getDefaultRepaymentPeriods() {
        return maxRepaymentPeriods;
    }

    // ── Domain behaviour ──────────────────────────────────────────────────

    /**
     * Checks whether a member qualifies for this loan product.
     * Returns a result object describing pass/fail with reason.
     */
    public EligibilityResult checkEligibility(
            int membershipMonths,
            int sharesOwned,
            Money savingsBalance,
            Money sharesValue,
            boolean hasArrears,
            int activeLoanCount) {

        if (!active) {
            return EligibilityResult.fail("Loan product is not currently available");
        }
        if (membershipMonths < minimumMembershipMonths) {
            return EligibilityResult.fail(
                "Must be a member for at least %d months (you have %d)"
                    .formatted(minimumMembershipMonths, membershipMonths));
        }
        if (sharesOwned < minimumSharesOwned) {
            return EligibilityResult.fail(
                "Must own at least %d shares (you own %d)"
                    .formatted(minimumSharesOwned, sharesOwned));
        }
        if (requiresZeroArrears && hasArrears) {
            return EligibilityResult.fail("You have outstanding arrears. Clear them before applying.");
        }
        if (activeLoanCount >= maxConcurrentLoans) {
            return EligibilityResult.fail(
                "You already have %d active loan(s) under this product (max %d)"
                    .formatted(activeLoanCount, maxConcurrentLoans));
        }
        return EligibilityResult.pass();
    }

    /**
     * Returns the maximum amount this member can borrow under this product.
     */
    public Money maximumLoanFor(Money savingsBalance, Money sharesValue) {
        Money cap = maximumAmount;

        if (maxMultipleOfSavings != null) {
            Money savingsCap = savingsBalance.multiply(maxMultipleOfSavings);
            if (savingsCap.isLessThan(cap)) cap = savingsCap;
        }
        if (maxMultipleOfSharesValue != null) {
            Money sharesCap = sharesValue.multiply(maxMultipleOfSharesValue);
            if (sharesCap.isLessThan(cap)) cap = sharesCap;
        }
        return cap;
    }

    public record EligibilityResult(boolean eligible, String reason) {
        static EligibilityResult pass() { return new EligibilityResult(true, null); }
        static EligibilityResult fail(String reason) { return new EligibilityResult(false, reason); }
    }
}
