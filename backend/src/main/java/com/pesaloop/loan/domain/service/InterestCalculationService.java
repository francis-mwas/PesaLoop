package com.pesaloop.loan.domain.service;

import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import com.pesaloop.shared.domain.Money;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure domain service for all interest calculations.
 * No Spring, no JPA — fully testable with plain JUnit.
 *
 * Supports the full matrix of:
 *   InterestType:           FLAT | REDUCING_BALANCE | SIMPLE_INTEREST
 *   AccrualFrequency:       FLAT_RATE | DAILY | WEEKLY | FORTNIGHTLY |
 *                           MONTHLY | QUARTERLY | ANNUALLY | CUSTOM
 *
 * Key design rule: all intermediate calculations use BigDecimal with 10 decimal places.
 * Final monetary results are rounded to 2 decimal places using HALF_EVEN.
 * This is how we avoid the KES -495 rounding bug seen in the chamaa spreadsheet.
 */
@Slf4j
@org.springframework.stereotype.Service
public class InterestCalculationService {

    private static final int CALC_SCALE = 10;
    private static final RoundingMode CALC_ROUNDING = RoundingMode.HALF_EVEN;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Calculates total interest for a FLAT_RATE loan (one-time charge at disbursement).
     *
     * Formula: interest = principal × rate
     * The rate is applied once regardless of repayment period.
     * Most common in informal chamaas — "borrow 10,000, pay back 11,000".
     *
     * Example from the real chamaa:
     *   Alice Watiri: loan 100,000 × 10% flat = 10,000 interest (appears unchanged across months)
     */
    public Money calculateFlatRateInterest(Money principal, BigDecimal rate) {
        return principal.percentage(rate.multiply(BigDecimal.valueOf(100)));
    }

    /**
     * Calculates interest accrued for ONE period on a reducing-balance loan.
     * Called by the scheduler each accrual period.
     *
     * Formula: interest = outstandingPrincipal × periodRate
     * where periodRate = annualRate / periodsPerYear
     *
     * Example: 12% per annum monthly reducing balance on KES 100,000:
     *   Period rate = 12% / 12 = 1%
     *   Month 1 interest = 100,000 × 1% = 1,000
     *   If 8,333 principal repaid → Month 2 interest = 91,667 × 1% = 916.67
     */
    public Money calculateReducingBalanceInterestForPeriod(
            Money outstandingPrincipal,
            BigDecimal annualRate,
            InterestAccrualFrequency frequency,
            Integer customIntervalDays) {

        BigDecimal periodsPerYear = periodsPerYear(frequency, customIntervalDays);
        BigDecimal periodRate = annualRate.divide(periodsPerYear, CALC_SCALE, CALC_ROUNDING);
        return outstandingPrincipal.percentage(periodRate.multiply(BigDecimal.valueOf(100)));
    }

    /**
     * Calculates simple interest for a given number of days.
     * I = P × R × (days / 365)
     *
     * Used for short-term loans where the exact number of days matters.
     */
    public Money calculateSimpleInterest(
            Money principal,
            BigDecimal annualRate,
            long days) {
        BigDecimal timeFraction = BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(365), CALC_SCALE, CALC_ROUNDING);
        BigDecimal rateDecimal = annualRate.divide(BigDecimal.valueOf(100), CALC_SCALE, CALC_ROUNDING);
        return principal.multiply(rateDecimal.multiply(timeFraction));
    }

    // ── Amortization schedule generation ─────────────────────────────────

    /**
     * Generates a full repayment schedule for a loan at disbursement time.
     * Returns a list of RepaymentInstallment — one per repayment period.
     *
     * Handles all three interest models:
     *   FLAT:              equal installments (principal + flat interest) / numPeriods
     *   REDUCING_BALANCE:  decreasing interest, equal principal payments
     *   SIMPLE_INTEREST:   one installment (bullet) with I = P×R×T
     */
    public List<RepaymentInstallment> generateSchedule(
            UUID loanId,
            UUID groupId,
            Money principal,
            LoanProduct product,
            LocalDate disbursementDate) {

        if (product.isBulletRepayment()) {
            return generateBulletSchedule(loanId, groupId, principal, product, disbursementDate);
        }

        return switch (product.getInterestType()) {
            case FLAT             -> generateFlatSchedule(loanId, groupId, principal, product, disbursementDate);
            case REDUCING_BALANCE -> generateReducingBalanceSchedule(loanId, groupId, principal, product, disbursementDate);
            case SIMPLE_INTEREST  -> generateSimpleInterestSchedule(loanId, groupId, principal, product, disbursementDate);
        };
    }


    /**
     * Calculates total interest for a loan at approval time.
     * Dispatches to the correct calculation based on interest type.
     *
     * For FLAT / SIMPLE_INTEREST: interest is fixed at approval.
     * For REDUCING_BALANCE: interest at approval time is an estimate
     * (the actual accrued interest will vary based on repayment behaviour —
     * the nightly scheduler handles ongoing accrual).
     */
    public Money calculateInterest(Money principal, LoanProduct product, int periods) {
        return switch (product.getInterestType()) {
            case FLAT -> calculateFlatRateInterest(principal, product.getInterestRate());
            case REDUCING_BALANCE -> {
                // Estimate total interest assuming no early repayments
                // Actual will be less if member repays early (reducing balance benefit)
                BigDecimal periodsPerYear = periodsPerYear(
                        product.getRepaymentFrequency(), product.getCustomAccrualIntervalDays());
                BigDecimal periodRate = product.getInterestRate()
                        .divide(periodsPerYear, CALC_SCALE, CALC_ROUNDING);
                Money total = Money.ofKes(BigDecimal.ZERO);
                Money balance = principal;
                Money principalPerPeriod = principal.divide(BigDecimal.valueOf(periods));
                for (int i = 0; i < periods; i++) {
                    total = total.add(balance.percentage(
                            periodRate.multiply(BigDecimal.valueOf(100))));
                    balance = balance.subtract(principalPerPeriod);
                }
                yield total;
            }
            case SIMPLE_INTEREST -> {
                long daysPerPeriod = switch (product.getRepaymentFrequency()) {
                    case MONTHLY     -> 30L;
                    case QUARTERLY   -> 90L;
                    case WEEKLY      -> 7L;
                    case FORTNIGHTLY -> 14L;
                    case ANNUALLY    -> 365L;
                    case CUSTOM -> product.getCustomAccrualIntervalDays() != null
                            ? product.getCustomAccrualIntervalDays() : 30L;
                    default -> 30L;
                };
                yield calculateSimpleInterest(principal, product.getInterestRate(), daysPerPeriod * periods);
            }
        };
    }

    /**
     * Overload accepting a periods override — used by ApproveLoanUseCase when the
     * approved periods differ from the product default.
     */
    public List<RepaymentInstallment> generateSchedule(
            UUID loanId, UUID groupId, Money principal, Money preCalculatedInterest,
            LoanProduct product, int periods, LocalDate disbursementDate) {

        // For FLAT rate: use the pre-calculated interest directly
        // For REDUCING_BALANCE: ignore pre-calculated interest, generate from product
        // (the schedule will have decreasing interest installments)
        if (product.getInterestType() == com.pesaloop.group.domain.model.InterestType.FLAT) {
            return generateFlatScheduleWithInterest(
                    loanId, groupId, principal, preCalculatedInterest, periods,
                    product.getRepaymentFrequency(), product.getCustomAccrualIntervalDays(),
                    disbursementDate);
        }
        // For all other types, use the standard product-driven schedule
        return generateSchedule(loanId, groupId, principal, product, disbursementDate);
    }

    /**
     * FLAT schedule with explicit interest amount (used when admin overrides amount/periods at approval).
     */
    private List<RepaymentInstallment> generateFlatScheduleWithInterest(
            UUID loanId, UUID groupId, Money principal, Money totalInterest,
            int periods, com.pesaloop.group.domain.model.InterestAccrualFrequency frequency,
            Integer customDays, LocalDate disbursementDate) {

        Money principalPerPeriod = principal.divide(BigDecimal.valueOf(periods));
        Money interestPerPeriod  = totalInterest.divide(BigDecimal.valueOf(periods));

        List<RepaymentInstallment> schedule = new ArrayList<>();
        Money balance = principal;
        LocalDate dueDate = disbursementDate;

        for (int i = 1; i <= periods; i++) {
            dueDate = nextDueDate(dueDate, frequency, customDays);
            Money pDue = (i == periods) ? balance.subtract(principalPerPeriod.multiply(periods - 1)) : principalPerPeriod;
            Money iDue = (i == periods) ? totalInterest.subtract(interestPerPeriod.multiply(periods - 1)) : interestPerPeriod;
            balance = balance.subtract(pDue);
            schedule.add(buildInstallment(loanId, groupId, i, dueDate, pDue, iDue, balance));
        }
        return schedule;
    }

    // ── Private schedule generators ───────────────────────────────────────

    /**
     * FLAT RATE schedule:
     * Total interest = principal × rate (one-time)
     * Each installment = (principal + totalInterest) / numPeriods
     * Principal per installment = principal / numPeriods
     * Interest per installment = totalInterest / numPeriods
     */
    private List<RepaymentInstallment> generateFlatSchedule(
            UUID loanId, UUID groupId, Money principal, LoanProduct product, LocalDate disbursementDate) {

        int periods = product.getMaxRepaymentPeriods();
        Money totalInterest = calculateFlatRateInterest(principal,
                product.getInterestRate().multiply(BigDecimal.valueOf(100)));

        Money principalPerPeriod = principal.divide(BigDecimal.valueOf(periods));
        Money interestPerPeriod  = totalInterest.divide(BigDecimal.valueOf(periods));
        Money installmentAmount  = principalPerPeriod.add(interestPerPeriod);

        List<RepaymentInstallment> schedule = new ArrayList<>();
        Money runningBalance = principal;
        LocalDate dueDate = disbursementDate;

        for (int i = 1; i <= periods; i++) {
            dueDate = nextDueDate(dueDate, product.getRepaymentFrequency(),
                    product.getCustomAccrualIntervalDays());

            // Last installment absorbs any rounding remainder
            Money pDue = (i == periods)
                    ? runningBalance.subtract(principalPerPeriod.multiply(periods - 1))
                    : principalPerPeriod;
            Money iDue = (i == periods)
                    ? totalInterest.subtract(interestPerPeriod.multiply(periods - 1))
                    : interestPerPeriod;

            runningBalance = runningBalance.subtract(pDue);

            schedule.add(buildInstallment(loanId, groupId, i, dueDate, pDue, iDue, runningBalance));
        }
        return schedule;
    }

    /**
     * REDUCING BALANCE schedule:
     * Equal principal payments each period.
     * Interest calculated on outstanding balance — decreases over time.
     * Total payment per period decreases (unlike FLAT where it's constant).
     */
    private List<RepaymentInstallment> generateReducingBalanceSchedule(
            UUID loanId, UUID groupId, Money principal, LoanProduct product, LocalDate disbursementDate) {

        int periods = product.getMaxRepaymentPeriods();
        Money principalPerPeriod = principal.divide(BigDecimal.valueOf(periods));
        BigDecimal periodsPerYear = periodsPerYear(product.getRepaymentFrequency(),
                product.getCustomAccrualIntervalDays());
        BigDecimal periodRate = product.getInterestRate()
                .divide(periodsPerYear, CALC_SCALE, CALC_ROUNDING);

        List<RepaymentInstallment> schedule = new ArrayList<>();
        Money balance = principal;
        LocalDate dueDate = disbursementDate;

        for (int i = 1; i <= periods; i++) {
            dueDate = nextDueDate(dueDate, product.getRepaymentFrequency(),
                    product.getCustomAccrualIntervalDays());

            Money interest = balance.percentage(
                    periodRate.multiply(BigDecimal.valueOf(100)));

            Money pDue = (i == periods)
                    ? balance
                    : principalPerPeriod;

            balance = balance.subtract(pDue);

            schedule.add(buildInstallment(loanId, groupId, i, dueDate, pDue, interest, balance));
        }
        return schedule;
    }

    /**
     * SIMPLE INTEREST bullet schedule — one installment.
     * I = P × R × T (time in years calculated from disbursement to due date)
     */
    private List<RepaymentInstallment> generateSimpleInterestSchedule(
            UUID loanId, UUID groupId, Money principal, LoanProduct product, LocalDate disbursementDate) {

        int periods = product.getMaxRepaymentPeriods();
        LocalDate finalDue = disbursementDate;
        for (int i = 0; i < periods; i++) {
            finalDue = nextDueDate(finalDue, product.getRepaymentFrequency(),
                    product.getCustomAccrualIntervalDays());
        }

        long days = ChronoUnit.DAYS.between(disbursementDate, finalDue);
        Money interest = calculateSimpleInterest(principal, product.getInterestRate()
                .multiply(BigDecimal.valueOf(100)), days);

        return List.of(buildInstallment(loanId, groupId, 1, finalDue,
                principal, interest, Money.ofKes(BigDecimal.ZERO)));
    }

    /**
     * BULLET schedule — single repayment for all models.
     */
    private List<RepaymentInstallment> generateBulletSchedule(
            UUID loanId, UUID groupId, Money principal, LoanProduct product, LocalDate disbursementDate) {

        LocalDate dueDate = nextDueDate(disbursementDate, product.getRepaymentFrequency(),
                product.getCustomAccrualIntervalDays());

        Money interest = switch (product.getInterestType()) {
            case FLAT            -> calculateFlatRateInterest(principal,
                                       product.getInterestRate().multiply(BigDecimal.valueOf(100)));
            case SIMPLE_INTEREST -> {
                long days = ChronoUnit.DAYS.between(disbursementDate, dueDate);
                yield calculateSimpleInterest(principal,
                        product.getInterestRate().multiply(BigDecimal.valueOf(100)), days);
            }
            case REDUCING_BALANCE -> calculateReducingBalanceInterestForPeriod(
                    principal, product.getInterestRate(),
                    product.getRepaymentFrequency(), product.getCustomAccrualIntervalDays());
        };

        return List.of(buildInstallment(loanId, groupId, 1, dueDate,
                principal, interest, Money.ofKes(BigDecimal.ZERO)));
    }

    // ── Dividend / interest pool distribution ─────────────────────────────

    /**
     * Calculates each member's share of the interest pool at year-end.
     *
     * Formula confirmed from real chamaa data:
     *   memberDividend = memberSavings + (memberSavings / totalSavings) × interestPool
     *
     * @param memberSavings   this member's total savings contributions for the year
     * @param totalSavings    sum of ALL members' savings for the year
     * @param interestPool    total interest collected on loans during the year
     * @return                this member's dividend (savings returned + interest share)
     */
    public Money calculateMemberDividend(
            Money memberSavings,
            Money totalSavings,
            Money interestPool) {

        if (totalSavings.isZero()) return memberSavings;

        BigDecimal proportion = memberSavings.getAmount()
                .divide(totalSavings.getAmount(), CALC_SCALE, CALC_ROUNDING);

        Money interestShare = interestPool.multiply(proportion);
        return memberSavings.add(interestShare);
    }

    /**
     * Share-based variant: uses shares owned rather than savings amount.
     * Some groups prefer this — dividend proportional to shares, not total contributions.
     *
     * @param memberShares    number of shares this member owns
     * @param totalGroupShares total shares across all members
     * @param interestPool    total interest pool to distribute
     */
    public Money calculateShareBasedInterestShare(
            int memberShares,
            int totalGroupShares,
            Money interestPool) {

        if (totalGroupShares == 0) return Money.ofKes(BigDecimal.ZERO);
        BigDecimal proportion = BigDecimal.valueOf(memberShares)
                .divide(BigDecimal.valueOf(totalGroupShares), CALC_SCALE, CALC_ROUNDING);
        return interestPool.multiply(proportion);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns how many accrual periods fit in one year for the given frequency.
     * Used to derive the per-period rate from the annual rate.
     */
    public BigDecimal periodsPerYear(InterestAccrualFrequency frequency, Integer customDays) {
        return switch (frequency) {
            case DAILY       -> BigDecimal.valueOf(365);
            case WEEKLY      -> BigDecimal.valueOf(52);
            case FORTNIGHTLY -> BigDecimal.valueOf(26);
            case MONTHLY     -> BigDecimal.valueOf(12);
            case QUARTERLY   -> BigDecimal.valueOf(4);
            case ANNUALLY    -> BigDecimal.valueOf(1);
            case FLAT_RATE   -> BigDecimal.valueOf(1);  // not used for periodic accrual
            case CUSTOM      -> {
                int days = (customDays != null && customDays > 0) ? customDays : 30;
                yield BigDecimal.valueOf(365.0 / days).setScale(CALC_SCALE, CALC_ROUNDING);
            }
        };
    }

    private LocalDate nextDueDate(LocalDate from, InterestAccrualFrequency freq, Integer customDays) {
        return switch (freq) {
            case DAILY       -> from.plusDays(1);
            case WEEKLY      -> from.plusWeeks(1);
            case FORTNIGHTLY -> from.plusWeeks(2);
            case MONTHLY     -> from.plusMonths(1);
            case QUARTERLY   -> from.plusMonths(3);
            case ANNUALLY    -> from.plusYears(1);
            case FLAT_RATE   -> from.plusMonths(1);  // treat as monthly for schedule purposes
            case CUSTOM      -> from.plusDays(customDays != null ? customDays : 30);
        };
    }

    private RepaymentInstallment buildInstallment(
            UUID loanId, UUID groupId, int number, LocalDate dueDate,
            Money principalDue, Money interestDue, Money balanceAfter) {

        return RepaymentInstallment.builder()
                .id(UUID.randomUUID())
                .loanId(loanId)
                .groupId(groupId)
                .installmentNumber(number)
                .dueDate(dueDate)
                .principalDue(principalDue)
                .interestDue(interestDue)
                .totalDue(principalDue.add(interestDue))
                .balanceAfter(balanceAfter)
                .principalPaid(Money.ofKes(BigDecimal.ZERO))
                .interestPaid(Money.ofKes(BigDecimal.ZERO))
                .penaltyPaid(Money.ofKes(BigDecimal.ZERO))
                .status(RepaymentInstallment.InstallmentStatus.PENDING)
                .build();
    }
}
