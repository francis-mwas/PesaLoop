package com.pesaloop.loan;

import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import com.pesaloop.loan.domain.service.InterestCalculationService;
import com.pesaloop.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterestCalculationService")
class InterestCalculationServiceTest {

    private InterestCalculationService service;

    @BeforeEach
    void setUp() {
        service = new InterestCalculationService();
    }

    // ── Flat rate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Flat rate interest")
    class FlatRate {

        @Test
        @DisplayName("Alice Watiri: KES 100,000 at 10% flat = KES 10,000")
        void aliceWatiriCase() {
            // Exactly the chamaa spreadsheet case
            Money principal = Money.ofKes(100_000);
            Money interest = service.calculateFlatRateInterest(principal, new BigDecimal("10"));
            assertThat(interest.getAmount()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("KES 75,000 at 10% flat = KES 7,500")
        void standardFlatRate() {
            Money interest = service.calculateFlatRateInterest(
                    Money.ofKes(75_000), new BigDecimal("10"));
            assertThat(interest.getAmount()).isEqualByComparingTo("7500.00");
        }

        @Test
        @DisplayName("Generates correct flat schedule — 3 equal installments")
        void flatSchedule() {
            LoanProduct product = testProduct(InterestType.FLAT,
                    InterestAccrualFrequency.FLAT_RATE, "0.10", 3);

            List<RepaymentInstallment> schedule = service.generateSchedule(
                    UUID.randomUUID(), UUID.randomUUID(),
                    Money.ofKes(90_000), product,
                    LocalDate.of(2024, 1, 15));

            assertThat(schedule).hasSize(3);

            // Each installment: principal = 30,000, interest = 3,000, total = 33,000
            schedule.forEach(inst -> {
                assertThat(inst.getPrincipalDue().getAmount()).isEqualByComparingTo("30000.00");
                assertThat(inst.getInterestDue().getAmount()).isEqualByComparingTo("3000.00");
                assertThat(inst.getTotalDue().getAmount()).isEqualByComparingTo("33000.00");
            });

            // Total principal across all installments = original principal
            BigDecimal totalPrincipal = schedule.stream()
                    .map(i -> i.getPrincipalDue().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalPrincipal).isEqualByComparingTo("90000.00");
        }

        @Test
        @DisplayName("Rounding: schedule totals exactly match — no KES -495 bug")
        void noRoundingBug() {
            // KES 100,000 loan, 10% flat, 3 installments
            // The chamaa had a -495 deficit — we must not repeat that
            LoanProduct product = testProduct(InterestType.FLAT,
                    InterestAccrualFrequency.FLAT_RATE, "0.10", 3);

            List<RepaymentInstallment> schedule = service.generateSchedule(
                    UUID.randomUUID(), UUID.randomUUID(),
                    Money.ofKes(100_000), product,
                    LocalDate.of(2024, 1, 1));

            BigDecimal totalPrincipal = schedule.stream()
                    .map(i -> i.getPrincipalDue().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalInterest = schedule.stream()
                    .map(i -> i.getInterestDue().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalPrincipal).isEqualByComparingTo("100000.00");
            assertThat(totalInterest).isEqualByComparingTo("10000.00");
        }
    }

    // ── Reducing balance ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reducing balance interest")
    class ReducingBalance {

        @Test
        @DisplayName("Period rate = annual rate / periods per year")
        void periodRate() {
            // 12% annual, monthly = 1% per month
            Money interest = service.calculateReducingBalanceInterestForPeriod(
                    Money.ofKes(100_000),
                    new BigDecimal("0.12"),
                    InterestAccrualFrequency.MONTHLY,
                    null);
            assertThat(interest.getAmount()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("Daily rate: 36.5% annual on KES 10,000 = KES 10 per day")
        void dailyRate() {
            Money interest = service.calculateReducingBalanceInterestForPeriod(
                    Money.ofKes(10_000),
                    new BigDecimal("0.365"),
                    InterestAccrualFrequency.DAILY,
                    null);
            assertThat(interest.getAmount()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Reducing balance: interest decreases each period")
        void interestDecreases() {
            LoanProduct product = testProduct(InterestType.REDUCING_BALANCE,
                    InterestAccrualFrequency.MONTHLY, "0.12", 12);

            List<RepaymentInstallment> schedule = service.generateSchedule(
                    UUID.randomUUID(), UUID.randomUUID(),
                    Money.ofKes(120_000), product,
                    LocalDate.of(2024, 1, 1));

            assertThat(schedule).hasSize(12);

            // Interest should decrease with each installment
            for (int i = 1; i < schedule.size(); i++) {
                assertThat(schedule.get(i).getInterestDue().getAmount())
                        .isLessThan(schedule.get(i - 1).getInterestDue().getAmount());
            }

            // Total principal repaid = original principal
            BigDecimal totalPrincipal = schedule.stream()
                    .map(inst -> inst.getPrincipalDue().getAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalPrincipal).isEqualByComparingTo("120000.00");
        }
    }

    // ── Dividend calculation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Year-end dividend distribution")
    class Dividend {

        @Test
        @DisplayName("Njeri Kahara: 60,000 savings out of 645,000 total, 213,495 interest pool")
        void njeriKaharaCase() {
            // Real data from the chamaa spreadsheet
            Money njeriSavings   = Money.ofKes(60_000);
            Money totalSavings   = Money.ofKes(645_000);
            Money interestPool   = Money.ofKes(new BigDecimal("213495.00"));

            Money dividend = service.calculateMemberDividend(njeriSavings, totalSavings, interestPool);

            // Expected: 60,000 + (60,000/645,000 × 213,495)
            // = 60,000 + 19,860.00 = 79,860.00
            BigDecimal expectedInterestShare = new BigDecimal("213495.00")
                    .multiply(new BigDecimal("60000"))
                    .divide(new BigDecimal("645000"), 2, java.math.RoundingMode.HALF_EVEN);
            BigDecimal expectedTotal = new BigDecimal("60000.00").add(expectedInterestShare);

            assertThat(dividend.getAmount()).isEqualByComparingTo(expectedTotal);
        }

        @Test
        @DisplayName("All dividends sum to total savings + interest pool")
        void dividendsSumCorrectly() {
            // 3-member group for simplicity
            Money[] savings = {
                    Money.ofKes(51_000),  // Alice: 17 shares
                    Money.ofKes(45_000),  // Beatrice: 15 shares
                    Money.ofKes(75_000)   // Daniel: 25 shares
            };
            Money totalSavings = Money.ofKes(171_000);
            Money interestPool = Money.ofKes(20_000);
            Money expectedTotal = totalSavings.add(interestPool);

            Money sumOfDividends = Money.ofKes(BigDecimal.ZERO);
            for (Money s : savings) {
                sumOfDividends = sumOfDividends.add(
                        service.calculateMemberDividend(s, totalSavings, interestPool));
            }

            // Allow 1 cent tolerance for rounding across 3 members
            BigDecimal diff = expectedTotal.getAmount().subtract(sumOfDividends.getAmount()).abs();
            assertThat(diff).isLessThanOrEqualTo(new BigDecimal("0.02"));
        }

        @Test
        @DisplayName("Share-based: 17 shares out of 57 total get correct proportion")
        void shareBasedInterestShare() {
            Money interestPool = Money.ofKes(213_495);
            Money aliceShare = service.calculateShareBasedInterestShare(17, 57, interestPool);

            BigDecimal expected = new BigDecimal("213495")
                    .multiply(new BigDecimal("17"))
                    .divide(new BigDecimal("57"), 2, java.math.RoundingMode.HALF_EVEN);

            assertThat(aliceShare.getAmount()).isEqualByComparingTo(expected);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private LoanProduct testProduct(InterestType interestType,
                                    InterestAccrualFrequency frequency,
                                    String rate,
                                    int periods) {
        return LoanProduct.builder()
                .id(UUID.randomUUID())
                .groupId(UUID.randomUUID())
                .name("Test Product")
                .active(true)
                .interestType(interestType)
                .accrualFrequency(frequency)
                .interestRate(new BigDecimal(rate))
                .minimumAmount(Money.ofKes(1_000))
                .maximumAmount(Money.ofKes(1_000_000))
                .maxRepaymentPeriods(periods)
                .repaymentFrequency(InterestAccrualFrequency.MONTHLY)
                .bulletRepayment(false)
                .minimumMembershipMonths(0)
                .minimumSharesOwned(1)
                .requiresGuarantor(false)
                .maxGuarantors(0)
                .requiresZeroArrears(false)
                .maxConcurrentLoans(3)
                .lateRepaymentPenaltyRate(new BigDecimal("0.05"))
                .penaltyGracePeriodDays(3)
                .build();
    }
}
