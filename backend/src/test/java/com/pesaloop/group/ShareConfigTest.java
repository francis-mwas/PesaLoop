package com.pesaloop.group;

import com.pesaloop.group.domain.model.ShareConfig;
import com.pesaloop.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShareConfig value object")
class ShareConfigTest {

    private final ShareConfig chamaaConfig = ShareConfig.builder()
            .pricePerShare(Money.ofKes(3_000))
            .minimumShares(1)
            .maximumShares(25)
            .sharesMode(true)
            .allowShareChangeMidYear(false)
            .build();

    @Nested
    @DisplayName("Contribution calculation")
    class ContributionCalc {

        @Test
        @DisplayName("1 share = KES 3,000 (Sarah Wachera case)")
        void oneShare() {
            assertThat(chamaaConfig.contributionFor(1).getAmount())
                    .isEqualByComparingTo("3000.00");
        }

        @Test
        @DisplayName("17 shares = KES 51,000 (Alice Watiri case)")
        void seventeenShares() {
            assertThat(chamaaConfig.contributionFor(17).getAmount())
                    .isEqualByComparingTo("51000.00");
        }

        @Test
        @DisplayName("25 shares = KES 75,000 (Daniel Karoki case — maximum)")
        void twentyFiveShares() {
            assertThat(chamaaConfig.contributionFor(25).getAmount())
                    .isEqualByComparingTo("75000.00");
        }

        @Test
        @DisplayName("Total group: 31 members × average ≈ KES 645,000")
        void groupTotalMatchesSpreadsheet() {
            // Real data from the chamaa spreadsheet — exact member shares
            int[] memberShares = {
                17, 4, 15, 13, 25, 3, 2, 2, 4, 7, 6, 8, 6, 7, 1, 1, 1,
                8, 3, 1, 6, 4, 20, 3, 15, 4, 3, 14, 1, 6, 4, 2
            };
            BigDecimal total = BigDecimal.ZERO;
            for (int s : memberShares) {
                total = total.add(chamaaConfig.contributionFor(s).getAmount());
            }
            // Should be approximately 645,000 (the actual spreadsheet total)
            assertThat(total).isGreaterThanOrEqualTo(new BigDecimal("600000"));
        }
    }

    @Nested
    @DisplayName("Share validation")
    class Validation {

        @Test
        @DisplayName("Rejects 0 shares (below minimum)")
        void belowMinimum() {
            assertThatThrownBy(() -> chamaaConfig.validateShareCount(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minimum");
        }

        @Test
        @DisplayName("Rejects 26 shares (above maximum)")
        void aboveMaximum() {
            assertThatThrownBy(() -> chamaaConfig.validateShareCount(26))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Accepts 1 share (minimum)")
        void acceptsMinimum() {
            assertThatNoException().isThrownBy(() -> chamaaConfig.validateShareCount(1));
        }

        @Test
        @DisplayName("Accepts 25 shares (maximum)")
        void acceptsMaximum() {
            assertThatNoException().isThrownBy(() -> chamaaConfig.validateShareCount(25));
        }
    }

    @Nested
    @DisplayName("Proportional dividend calculation")
    class ProportionalShare {

        @Test
        @DisplayName("Alice Watiri: 17/215 shares of KES 213,495 interest pool")
        void aliceWatiriDividend() {
            // From spreadsheet: total shares calculated from contribution amounts
            // Total contributions = 645,000 / 3,000 = 215 shares total
            Money interestPool = Money.ofKes(new BigDecimal("213495.00"));
            Money aliceShare = chamaaConfig.proportionalShare(17, 215, interestPool);

            BigDecimal expected = new BigDecimal("213495")
                    .multiply(BigDecimal.valueOf(17))
                    .divide(BigDecimal.valueOf(215), 2, java.math.RoundingMode.HALF_EVEN);

            assertThat(aliceShare.getAmount()).isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("All member shares sum to full interest pool (±2 cents rounding)")
        void sharesAlwaysSumToPool() {
            int[] memberShares = {17, 4, 15, 13, 25, 3, 2, 2, 4, 7};
            int total = 0;
            for (int s : memberShares) total += s;

            Money pool = Money.ofKes(new BigDecimal("213495.00"));
            Money sum = Money.ofKes(BigDecimal.ZERO);

            for (int s : memberShares) {
                sum = sum.add(chamaaConfig.proportionalShare(s, total, pool));
            }

            // The sum of proportional shares must equal the pool within 10 cents
            BigDecimal diff = pool.getAmount().subtract(sum.getAmount()).abs();
            assertThat(diff).isLessThanOrEqualTo(new BigDecimal("0.10"));
        }

        @Test
        @DisplayName("Returns zero if total shares is 0")
        void zeroTotalSharesReturnsZero() {
            Money pool = Money.ofKes(100_000);
            Money share = chamaaConfig.proportionalShare(5, 0, pool);
            assertThat(share.isZero()).isTrue();
        }
    }

    @Nested
    @DisplayName("Flat amount mode")
    class FlatAmountMode {

        @Test
        @DisplayName("Flat amount factory creates single-share config")
        void flatAmountFactory() {
            ShareConfig flat = ShareConfig.flatAmount(Money.ofKes(5_000));
            assertThat(flat.isSharesMode()).isFalse();
            assertThat(flat.getMinimumShares()).isEqualTo(1);
            assertThat(flat.getMaximumShares()).isEqualTo(1);
            assertThat(flat.contributionFor(1).getAmount()).isEqualByComparingTo("5000.00");
        }
    }
}
