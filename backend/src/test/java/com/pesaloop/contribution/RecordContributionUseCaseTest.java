package com.pesaloop.contribution;

import com.pesaloop.contribution.application.dto.ContributionDtos.*;
import com.pesaloop.contribution.application.port.out.ContributionCycleRepository;
import com.pesaloop.contribution.application.port.out.ContributionEntryRepository;
import com.pesaloop.contribution.application.usecase.RecordContributionUseCase;
import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.domain.model.MemberStatus;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecordContributionUseCase")
class RecordContributionUseCaseTest {

    @Mock ContributionCycleRepository cycleRepository;
    @Mock ContributionEntryRepository entryRepository;
    @Mock MemberRepository memberRepository;

    @InjectMocks RecordContributionUseCase useCase;

    private UUID groupId, memberId, cycleId, entryId, userId;
    private ContributionCycle openCycle;
    private ContributionEntry pendingEntry;
    private Member activeMember;

    @BeforeEach
    void setUp() {
        groupId  = UUID.randomUUID();
        memberId = UUID.randomUUID();
        cycleId  = UUID.randomUUID();
        entryId  = UUID.randomUUID();
        userId   = UUID.randomUUID();

        TenantContext.set(groupId, userId, "TREASURER");

        openCycle = ContributionCycle.builder()
                .id(cycleId).groupId(groupId)
                .cycleNumber(4).year(2024)
                .dueDate(LocalDate.now().plusDays(5))
                .gracePeriodEnd(LocalDate.now().plusDays(8))
                .status(ContributionCycle.CycleStatus.OPEN)
                .totalExpected(Money.ofKes(645_000))
                .totalCollected(Money.ofKes(594_000))
                .totalArrears(Money.ofKes(BigDecimal.ZERO))
                .totalFinesIssued(Money.ofKes(BigDecimal.ZERO))
                .build();

        pendingEntry = ContributionEntry.builder()
                .id(entryId).groupId(groupId).cycleId(cycleId).memberId(memberId)
                .expectedAmount(Money.ofKes(51_000))
                .paidAmount(Money.ofKes(BigDecimal.ZERO))
                .arrearsCarriedForward(Money.ofKes(BigDecimal.ZERO))
                .status(ContributionEntry.EntryStatus.PENDING)
                .build();

        activeMember = Member.builder()
                .id(memberId).groupId(groupId).userId(userId)
                .memberNumber("M-017").status(MemberStatus.ACTIVE)
                .sharesOwned(17).joinedOn(LocalDate.now().minusYears(1))
                .savingsBalance(Money.ofKes(204_000))
                .arrearsBalance(Money.ofKes(BigDecimal.ZERO))
                .finesBalance(Money.ofKes(BigDecimal.ZERO))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Full payment marks entry as PAID")
        void fullPaymentMarksPaid() {
            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(openCycle));
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(activeMember));
            when(entryRepository.findByCycleIdAndMemberId(cycleId, memberId))
                    .thenReturn(Optional.of(pendingEntry));
            when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("51000"),
                    ContributionEntry.PaymentMethod.CASH, "RCPT-001", null);

            var result = useCase.execute(request, userId);

            assertThat(result.status()).isEqualTo(ContributionEntry.EntryStatus.PAID);
            assertThat(result.paidAmount()).isEqualByComparingTo("51000.00");
            assertThat(result.balance()).isEqualByComparingTo("0.00");

            verify(memberRepository).save(argThat(m ->
                    m.getSavingsBalance().getAmount().compareTo(new BigDecimal("255000.00")) == 0));
        }

        @Test
        @DisplayName("Partial payment keeps PARTIAL status")
        void partialPaymentStatus() {
            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(openCycle));
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(activeMember));
            when(entryRepository.findByCycleIdAndMemberId(cycleId, memberId))
                    .thenReturn(Optional.of(pendingEntry));
            when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("30000"),
                    ContributionEntry.PaymentMethod.CASH, null, null);

            var result = useCase.execute(request, userId);

            assertThat(result.status()).isEqualTo(ContributionEntry.EntryStatus.PARTIAL);
            assertThat(result.paidAmount()).isEqualByComparingTo("30000.00");
            assertThat(result.balance()).isEqualByComparingTo("21000.00");
        }

        @Test
        @DisplayName("Excess payment is applied to arrears")
        void excessAppliedToArrears() {
            Member memberWithArrears = Member.builder()
                    .id(memberId).groupId(groupId).userId(userId)
                    .memberNumber("M-017").status(MemberStatus.ACTIVE)
                    .sharesOwned(17).joinedOn(LocalDate.now().minusYears(1))
                    .savingsBalance(Money.ofKes(204_000))
                    .arrearsBalance(Money.ofKes(10_000))
                    .finesBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();

            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(openCycle));
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(memberWithArrears));
            when(entryRepository.findByCycleIdAndMemberId(cycleId, memberId))
                    .thenReturn(Optional.of(pendingEntry));
            when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(cycleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("51000"),
                    ContributionEntry.PaymentMethod.MPESA_PAYBILL, "QWE123", null);

            useCase.execute(request, userId);

            verify(memberRepository).save(argThat(m ->
                    m.getSavingsBalance().getAmount().compareTo(new BigDecimal("255000.00")) == 0));
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class Failures {

        @Test
        @DisplayName("Throws when cycle is CLOSED")
        void closedCycleThrows() {
            ContributionCycle closed = ContributionCycle.builder()
                    .id(cycleId).groupId(groupId).cycleNumber(3).year(2024)
                    .dueDate(LocalDate.now().minusDays(10))
                    .gracePeriodEnd(LocalDate.now().minusDays(7))
                    .status(ContributionCycle.CycleStatus.CLOSED)
                    .totalExpected(Money.ofKes(645_000))
                    .totalCollected(Money.ofKes(645_000))
                    .totalArrears(Money.ofKes(BigDecimal.ZERO))
                    .totalFinesIssued(Money.ofKes(BigDecimal.ZERO))
                    .build();

            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(closed));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("51000"),
                    ContributionEntry.PaymentMethod.CASH, null, null);

            assertThatThrownBy(() -> useCase.execute(request, userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("Throws when payment exceeds outstanding balance")
        void overpaymentThrows() {
            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(openCycle));
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(activeMember));
            when(entryRepository.findByCycleIdAndMemberId(cycleId, memberId))
                    .thenReturn(Optional.of(pendingEntry));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("60000"),
                    ContributionEntry.PaymentMethod.CASH, null, null);

            assertThatThrownBy(() -> useCase.execute(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("Throws when member is SUSPENDED")
        void suspendedMemberThrows() {
            Member suspended = Member.builder()
                    .id(memberId).groupId(groupId).userId(userId)
                    .memberNumber("M-017").status(MemberStatus.SUSPENDED)
                    .sharesOwned(17).joinedOn(LocalDate.now().minusYears(1))
                    .savingsBalance(Money.ofKes(204_000))
                    .arrearsBalance(Money.ofKes(BigDecimal.ZERO))
                    .finesBalance(Money.ofKes(BigDecimal.ZERO))
                    .build();

            when(cycleRepository.findById(cycleId)).thenReturn(Optional.of(openCycle));
            when(memberRepository.findById(memberId)).thenReturn(Optional.of(suspended));

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("51000"),
                    ContributionEntry.PaymentMethod.CASH, null, null);

            assertThatThrownBy(() -> useCase.execute(request, userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("suspended");
        }

        @Test
        @DisplayName("Throws when cycle not found")
        void cycleNotFoundThrows() {
            when(cycleRepository.findById(cycleId)).thenReturn(Optional.empty());

            var request = new RecordManualPaymentRequest(
                    memberId, cycleId, new BigDecimal("51000"),
                    ContributionEntry.PaymentMethod.CASH, null, null);

            assertThatThrownBy(() -> useCase.execute(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cycle not found");
        }
    }
}