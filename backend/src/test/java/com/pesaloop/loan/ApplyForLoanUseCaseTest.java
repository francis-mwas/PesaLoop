package com.pesaloop.loan;

import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.group.domain.model.*;
import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.loan.application.port.out.LoanProductRepository;
import com.pesaloop.loan.application.usecase.ApplyForLoanUseCase;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyForLoanUseCase")
class ApplyForLoanUseCaseTest {

    @Mock LoanProductRepository productRepository;
    @Mock LoanAccountRepository loanRepository;
    @Mock GroupRepository groupRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks ApplyForLoanUseCase useCase;

    private UUID groupId, memberId, userId, productId;
    private Group tableBankingGroup;
    private Member eligibleMember;
    private LoanProduct standardProduct;

    @BeforeEach
    void setUp() {
        groupId   = UUID.randomUUID();
        memberId  = UUID.randomUUID();
        userId    = UUID.randomUUID();
        productId = UUID.randomUUID();

        TenantContext.set(groupId, userId, "MEMBER");

        ShareConfig shareConfig = ShareConfig.builder()
                .pricePerShare(Money.ofKes(3_000))
                .minimumShares(1)
                .maximumShares(25)
                .sharesMode(true)
                .allowShareChangeMidYear(false)
                .maxTotalGroupShares(0)
                .build();

        tableBankingGroup = Group.create(
                "Wanjiku Table Banking",
                "wanjiku-table-banking",
                Set.of(GroupType.TABLE_BANKING),
                "KES",
                shareConfig,
                ContributionFrequency.MONTHLY,
                userId
        );

        eligibleMember = Member.builder()
                .id(memberId).groupId(groupId).userId(userId)
                .memberNumber("M-023").status(MemberStatus.ACTIVE)
                .sharesOwned(15)
                .joinedOn(LocalDate.now().minusMonths(6))
                .savingsBalance(Money.ofKes(90_000))
                .arrearsBalance(Money.ofKes(BigDecimal.ZERO))
                .finesBalance(Money.ofKes(BigDecimal.ZERO))
                .build();

        standardProduct = LoanProduct.builder()
                .id(productId).groupId(groupId)
                .name("Table Banking Loan")
                .active(true)
                .interestType(InterestType.FLAT)
                .accrualFrequency(InterestAccrualFrequency.FLAT_RATE)
                .interestRate(new BigDecimal("0.10"))
                .minimumAmount(Money.ofKes(1_000))
                .maximumAmount(Money.ofKes(500_000))
                .maxMultipleOfSavings(new BigDecimal("3.0"))
                .maxMultipleOfSharesValue(null)
                .maxRepaymentPeriods(3)
                .repaymentFrequency(InterestAccrualFrequency.MONTHLY)
                .bulletRepayment(false)
                .minimumMembershipMonths(3)
                .minimumSharesOwned(1)
                .requiresGuarantor(false)
                .maxGuarantors(0)
                .requiresZeroArrears(true)
                .maxConcurrentLoans(1)
                .lateRepaymentPenaltyRate(new BigDecimal("0.05"))
                .penaltyGracePeriodDays(3)
                .build();

        // Default: no existing loan book
        when(loanRepository.totalActiveLoanBook(groupId)).thenReturn(BigDecimal.ZERO);
        when(loanRepository.totalActiveLoansForMember(memberId)).thenReturn(BigDecimal.ZERO);
        when(loanRepository.nextLoanReference(any(), anyInt())).thenReturn("LN-2024-0001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Eligible applications")
    class Eligible {

        @Test
        @DisplayName("Valid application creates loan in PENDING_APPROVAL")
        void validApplicationCreatesLoan() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(eligibleMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new ApplyForLoanRequest(productId, new BigDecimal("150000"),
                    3, null, null, null, null);
            var result = useCase.execute(request, userId);

            assertThat(result.eligible()).isTrue();
            assertThat(result.status()).isEqualTo(LoanStatus.PENDING_APPROVAL);
            assertThat(result.loanId()).isNotNull();
            assertThat(result.loanReference()).isEqualTo("LN-2024-0001");
        }

        @Test
        @DisplayName("Max loan = 3× savings = KES 270,000")
        void maxLoanCalculation() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(eligibleMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);
            when(loanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new ApplyForLoanRequest(productId, new BigDecimal("270000"),
                    3, null, null, null, null);
            var result = useCase.execute(request, userId);

            assertThat(result.eligible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Ineligible applications — returns reason without persisting")
    class Ineligible {

        @Test
        @DisplayName("Rejects: membership too short")
        void membershipTooShort() {
            Member newMember = Member.builder()
                    .id(memberId).groupId(groupId).userId(userId)
                    .memberNumber("M-023").status(MemberStatus.ACTIVE)
                    .sharesOwned(15)
                    .joinedOn(LocalDate.now().minusMonths(2))
                    .savingsBalance(Money.ofKes(90_000))
                    .arrearsBalance(Money.ofKes(BigDecimal.ZERO))
                    .finesBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(newMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);

            var result = useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("50000"),
                            null, null, null, null, null),
                    userId);

            assertThat(result.eligible()).isFalse();
            assertThat(result.ineligibilityReason()).contains("3 months");
            assertThat(result.loanId()).isNull();
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects: has arrears")
        void hasArrearsBlocked() {
            Member memberWithArrears = Member.builder()
                    .id(memberId).groupId(groupId).userId(userId)
                    .memberNumber("M-023").status(MemberStatus.ACTIVE)
                    .sharesOwned(15)
                    .joinedOn(LocalDate.now().minusMonths(6))
                    .savingsBalance(Money.ofKes(90_000))
                    .arrearsBalance(Money.ofKes(3_000))
                    .finesBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();

            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(memberWithArrears));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);

            var result = useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("50000"),
                            null, null, null, null, null),
                    userId);

            assertThat(result.eligible()).isFalse();
            assertThat(result.ineligibilityReason()).containsIgnoringCase("arrears");
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects: amount exceeds 3× savings")
        void exceedsSavingsMultiple() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(eligibleMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);

            var result = useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("350000"),
                            null, null, null, null, null),
                    userId);

            assertThat(result.eligible()).isFalse();
            assertThat(result.ineligibilityReason()).containsIgnoringCase("maximum");
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects: already has active loan for this product")
        void concurrentLoanBlocked() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(eligibleMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(1);

            var result = useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("50000"),
                            null, null, null, null, null),
                    userId);

            assertThat(result.eligible()).isFalse();
            assertThat(result.ineligibilityReason()).contains("1 active");
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects: would exceed 35% loan book concentration — the Brian Kahara rule")
        void concentrationRuleBlocked() {
            // Existing loan book: 700,000. Member has 250,000.
            // Requesting 200,000 more → would hold 450,000 / 900,000 = 50% > 35%
            when(loanRepository.totalActiveLoanBook(groupId))
                    .thenReturn(new BigDecimal("700000"));
            when(loanRepository.totalActiveLoansForMember(memberId))
                    .thenReturn(new BigDecimal("250000"));

            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(tableBankingGroup));

            Member highSavingsMember = Member.builder()
                    .id(memberId).groupId(groupId).userId(userId)
                    .memberNumber("M-023").status(MemberStatus.ACTIVE)
                    .sharesOwned(15)
                    .joinedOn(LocalDate.now().minusMonths(6))
                    .savingsBalance(Money.ofKes(300_000))
                    .arrearsBalance(Money.ofKes(BigDecimal.ZERO))
                    .finesBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(highSavingsMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);

            var result = useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("200000"),
                            null, null, null, null, null),
                    userId);

            assertThat(result.eligible()).isFalse();
            assertThat(result.ineligibilityReason()).containsIgnoringCase("35");
            verify(loanRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects: group is not TABLE_BANKING type")
        void nonLendingGroupBlocked() {
            Group mgrOnlyGroup = Group.create(
                    "MGR Only Group", "mgr-group",
                    Set.of(GroupType.MERRY_GO_ROUND),
                    "KES",
                    ShareConfig.flatAmount(Money.ofKes(5_000)),
                    ContributionFrequency.MONTHLY,
                    userId
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(standardProduct));
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(mgrOnlyGroup));
            when(memberRepository.findByGroupIdAndUserId(groupId, userId))
                    .thenReturn(Optional.of(eligibleMember));
            when(loanRepository.countActiveByMemberIdAndProductId(memberId, productId)).thenReturn(0);

            assertThatThrownBy(() -> useCase.execute(
                    new ApplyForLoanRequest(productId, new BigDecimal("50000"),
                            null, null, null, null, null),
                    userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TABLE_BANKING");
        }
    }
}