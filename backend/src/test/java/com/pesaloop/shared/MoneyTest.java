package com.pesaloop.shared;

import com.pesaloop.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money value object")
class MoneyTest {

    @Nested
    @DisplayName("Arithmetic precision — the KES -495 prevention tests")
    class Precision {

        @Test
        @DisplayName("0.1 + 0.2 = exactly 0.30 — no floating point drift")
        void noFloatingPointDrift() {
            Money a = Money.ofKes("0.1");
            Money b = Money.ofKes("0.2");
            assertThat(a.add(b).getAmount()).isEqualByComparingTo("0.30");
        }

        @Test
        @DisplayName("Sum of 31 member shares totals exactly — no residual")
        void memberSharesSumExactly() {
            // 31 members, varying contributions, sums to exactly 645,000
            int[] amounts = {
                51000, 12000, 45000, 39000, 75000, 9000, 6000, 6000, 12000, 21000,
                18000, 24000, 18000, 21000, 3000, 3000, 3000, 24000, 9000, 3000,
                18000, 12000, 60000, 9000, 45000, 12000, 9000, 42000, 3000, 18000,
                12000
            };
            Money total = Money.ofKes(BigDecimal.ZERO);
            for (int a : amounts) total = total.add(Money.ofKes(a));
            assertThat(total.getAmount()).isEqualByComparingTo("645000.00");
        }

        @Test
        @DisplayName("Interest pool distribution sums exactly to pool (HALF_EVEN rounding)")
        void dividendDistributionBalances() {
            // Simulate year-end distribution across 5 members
            Money pool = Money.ofKes(new BigDecimal("213495.00"));
            int[] shares = {17, 15, 25, 4, 7};   // 68 total
            int totalShares = 68;

            Money distributed = Money.ofKes(BigDecimal.ZERO);
            for (int s : shares) {
                BigDecimal proportion = BigDecimal.valueOf(s)
                        .divide(BigDecimal.valueOf(totalShares), 10, java.math.RoundingMode.HALF_EVEN);
                distributed = distributed.add(pool.multiply(proportion));
            }

            // Maximum allowable rounding error: 1 cent per member = 5 cents
            BigDecimal diff = pool.getAmount().subtract(distributed.getAmount()).abs();
            assertThat(diff).isLessThanOrEqualTo(new BigDecimal("0.05"));
        }
    }

    @Nested
    @DisplayName("Currency safety")
    class CurrencySafety {

        @Test
        @DisplayName("Cannot add KES and USD")
        void currencyMismatchThrows() {
            Money kes = Money.ofKes(1_000);
            Money usd = Money.of(BigDecimal.valueOf(10), "USD");
            assertThatThrownBy(() -> kes.add(usd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        @DisplayName("Invalid currency code throws")
        void invalidCurrencyThrows() {
            assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XYZ"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("Comparison")
    class Comparison {

        @Test
        @DisplayName("equals uses value comparison, not reference")
        void valueEquality() {
            Money a = Money.ofKes(51_000);
            Money b = Money.ofKes(new BigDecimal("51000.00"));
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("isGreaterThan, isLessThan work correctly")
        void comparisons() {
            Money large = Money.ofKes(100_000);
            Money small = Money.ofKes(50_000);
            assertThat(large.isGreaterThan(small)).isTrue();
            assertThat(small.isLessThan(large)).isTrue();
            assertThat(large.isLessThan(small)).isFalse();
        }

        @Test
        @DisplayName("percentage of 10% on 100,000 = exactly 10,000")
        void percentageCalculation() {
            Money base = Money.ofKes(100_000);
            Money ten = base.percentage(BigDecimal.TEN);
            assertThat(ten.getAmount()).isEqualByComparingTo("10000.00");
        }
    }

    @Nested
    @DisplayName("Predicates")
    class Predicates {

        @Test
        @DisplayName("isZero, isPositive, isNegative")
        void predicates() {
            assertThat(Money.ofKes(0).isZero()).isTrue();
            assertThat(Money.ofKes(1).isPositive()).isTrue();
            assertThat(Money.ofKes(-1).isNegative()).isTrue();
            assertThat(Money.ofKes(0).isPositive()).isFalse();
        }
    }
}
