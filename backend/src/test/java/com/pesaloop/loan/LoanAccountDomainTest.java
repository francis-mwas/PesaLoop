package com.pesaloop.loan;

import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LoanAccount domain model")
class LoanAccountDomainTest {

    private LoanAccount activeLoan;

    @BeforeEach
    void setUp() {
        activeLoan = LoanAccount.builder()
                .id(UUID.randomUUID())
                .groupId(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .loanReference("LN-2024-0042")
                .status(LoanStatus.ACTIVE)
                .principalAmount(Money.ofKes(100_000))
                .totalInterestCharged(Money.ofKes(10_000))
                .principalBalance(Money.ofKes(100_000))
                .accruedInterest(Money.ofKes(10_000))
                .penaltyBalance(Money.ofKes(BigDecimal.ZERO))
                .totalInterestRepaid(Money.ofKes(BigDecimal.ZERO))
                .totalPrincipalRepaid(Money.ofKes(BigDecimal.ZERO))
                .guarantorMemberIds(new ArrayList<>())
                .build();
    }

    @Nested
    @DisplayName("Repayment allocation")
    class RepaymentAllocation {

        @Test
        @DisplayName("Allocates penalty → interest → principal in correct order")
        void allocationOrder() {
            // Add a penalty first
            activeLoan.applyPenalty(Money.ofKes(500));

            Money payment = Money.ofKes(5_500);
            var allocation = activeLoan.applyRepayment(payment, "REF001");

            // 500 to penalty, then 5000 to interest
            assertThat(allocation.toPenalty().getAmount()).isEqualByComparingTo("500.00");
            assertThat(allocation.toInterest().getAmount()).isEqualByComparingTo("5000.00");
            assertThat(allocation.toPrincipal().getAmount()).isEqualByComparingTo("0.00");

            // Verify balances updated
            assertThat(activeLoan.getPenaltyBalance().getAmount()).isEqualByComparingTo("0.00");
            assertThat(activeLoan.getAccruedInterest().getAmount()).isEqualByComparingTo("5000.00");
            assertThat(activeLoan.getPrincipalBalance().getAmount()).isEqualByComparingTo("100000.00");
        }

        @Test
        @DisplayName("Full settlement clears all balances and sets SETTLED")
        void fullSettlement() {
            // Total outstanding = 100,000 principal + 10,000 interest = 110,000
            Money fullPayment = Money.ofKes(110_000);
            var allocation = activeLoan.applyRepayment(fullPayment, "REF002");

            assertThat(allocation.toInterest().getAmount()).isEqualByComparingTo("10000.00");
            assertThat(allocation.toPrincipal().getAmount()).isEqualByComparingTo("100000.00");
            assertThat(activeLoan.totalOutstanding().isZero()).isTrue();
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.SETTLED);
            assertThat(activeLoan.getSettledAt()).isNotNull();
        }

        @Test
        @DisplayName("Partial principal repayment reduces balance correctly")
        void partialPrincipalRepayment() {
            // Pay interest first, then some principal
            Money fullInterest = Money.ofKes(10_000);
            activeLoan.applyRepayment(fullInterest, "REF003");

            Money principalPayment = Money.ofKes(30_000);
            activeLoan.applyRepayment(principalPayment, "REF004");

            assertThat(activeLoan.getPrincipalBalance().getAmount()).isEqualByComparingTo("70000.00");
            assertThat(activeLoan.getAccruedInterest().isZero()).isTrue();
            assertThat(activeLoan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        }

        @Test
        @DisplayName("Cannot repay more than outstanding")
        void overpaymentRejected() {
            Money overpayment = Money.ofKes(120_000);
            assertThatThrownBy(() -> activeLoan.applyRepayment(overpayment, "REF005"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("Cannot repay on non-ACTIVE loan")
        void repayOnInactiveThrows() {
            LoanAccount pendingLoan = activeLoan.toBuilder()
                    .status(LoanStatus.PENDING_APPROVAL)
                    .build();

            assertThatThrownBy(() -> pendingLoan.applyRepayment(Money.ofKes(1_000), "REF006"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not active");
        }
    }

    @Nested
    @DisplayName("Interest accrual")
    class InterestAccrual {

        @Test
        @DisplayName("Accrued interest adds correctly across multiple periods")
        void multipleAccrualPeriods() {
            Money dailyInterest = Money.ofKes(new BigDecimal("27.40")); // ~10% annual on 100K

            activeLoan.accrueInterest(dailyInterest);
            activeLoan.accrueInterest(dailyInterest);
            activeLoan.accrueInterest(dailyInterest);

            // 10,000 initial + 3 × 27.40
            BigDecimal expected = new BigDecimal("10000.00")
                    .add(new BigDecimal("27.40").multiply(BigDecimal.valueOf(3)));
            assertThat(activeLoan.getAccruedInterest().getAmount())
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("totalOutstanding sums principal + interest + penalty")
        void totalOutstandingCalculation() {
            activeLoan.applyPenalty(Money.ofKes(250));

            Money outstanding = activeLoan.totalOutstanding();
            // 100,000 + 10,000 + 250
            assertThat(outstanding.getAmount()).isEqualByComparingTo("110250.00");
        }
    }

    @Nested
    @DisplayName("Disbursement state machine")
    class DisbursementStateMachine {

        @Test
        @DisplayName("Disbursement transitions APPROVED → ACTIVE")
        void disbursementTransition() {
            LoanAccount approved = activeLoan.toBuilder()
                    .status(LoanStatus.APPROVED)
                    .principalBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();

            approved.disburse("MPesa-REF-XYZ", UUID.randomUUID());

            assertThat(approved.getStatus()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(approved.getPrincipalBalance().getAmount())
                    .isEqualByComparingTo("100000.00");
            assertThat(approved.getDisbursementMpesaRef()).isEqualTo("MPesa-REF-XYZ");
        }

        @Test
        @DisplayName("Cannot disburse non-APPROVED loan")
        void disbursementOnWrongStatus() {
            assertThatThrownBy(() -> activeLoan.disburse("REF", UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ACTIVE");
        }
    }
}
