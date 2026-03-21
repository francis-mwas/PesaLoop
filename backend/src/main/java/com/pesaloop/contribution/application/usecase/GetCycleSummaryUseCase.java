package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.GetCycleSummaryPort;

import com.pesaloop.contribution.application.dto.ContributionDtos.*;
import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.contribution.application.port.out.ContributionCycleRepository;
import com.pesaloop.contribution.application.port.out.ContributionEntryRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Returns a full snapshot of a contribution cycle:
 * - Cycle totals (expected, collected, outstanding)
 * - Per-member entry status
 * - MGR beneficiary details
 * - Paid / pending / late counts
 *
 * Used by the group admin dashboard and the treasurer's collection screen.
 */
@Service
@RequiredArgsConstructor
public class GetCycleSummaryUseCase implements GetCycleSummaryPort {

    private final ContributionCycleRepository cycleRepository;
    private final ContributionEntryRepository entryRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public CycleSummaryResponse execute(UUID cycleId) {

        ContributionCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new IllegalArgumentException("Cycle not found: " + cycleId));

        List<ContributionEntry> entries = entryRepository.findByCycleId(cycleId);

        long paidCount = entries.stream()
                .filter(e -> e.getStatus() == ContributionEntry.EntryStatus.PAID
                        || e.getStatus() == ContributionEntry.EntryStatus.WAIVED)
                .count();
        long pendingCount = entries.stream()
                .filter(e -> e.getStatus() == ContributionEntry.EntryStatus.PENDING
                        || e.getStatus() == ContributionEntry.EntryStatus.PARTIAL)
                .count();
        long lateCount = entries.stream()
                .filter(e -> e.getStatus() == ContributionEntry.EntryStatus.LATE
                        || e.getStatus() == ContributionEntry.EntryStatus.ARREARS_APPLIED)
                .count();

        // Resolve MGR beneficiary name if present
        String beneficiaryName = null;
        if (cycle.getMgrBeneficiaryMemberId() != null) {
            beneficiaryName = memberRepository.findFullNameById(cycle.getMgrBeneficiaryMemberId()).orElse(null);
        }

        return new CycleSummaryResponse(
                cycle.getId(),
                cycle.getCycleNumber(),
                cycle.getYear(),
                cycle.getDueDate(),
                cycle.getGracePeriodEnd(),
                cycle.getStatus().name(),
                cycle.getTotalExpected().getAmount(),
                cycle.getTotalCollected().getAmount(),
                cycle.shortfall().getAmount(),
                entries.size(),
                (int) paidCount,
                (int) pendingCount,
                (int) lateCount,
                cycle.getMgrBeneficiaryMemberId(),
                beneficiaryName,
                cycle.getMgrPayoutAmount() != null ? cycle.getMgrPayoutAmount().getAmount() : null
        );
    }
}
