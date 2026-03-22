package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.GetYearSummaryPort;
import com.pesaloop.contribution.application.port.out.GroupStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Assembles the year-end financial summary.
 * All data comes from GroupStatsRepository — business logic (dividendPerShare,
 * distributablePool) lives here, not in the controller or the adapter.
 */
@Service
@RequiredArgsConstructor
public class GetYearSummaryUseCase implements GetYearSummaryPort {

    private final GroupStatsRepository statsRepository;

    @Override
    @Transactional(readOnly = true)
    public YearSummaryResult execute(UUID groupId, int year) {
        BigDecimal totalContributions = statsRepository.findTotalContributionsByYear(groupId, year);
        BigDecimal interestCollected  = statsRepository.findTotalInterestCollected(groupId);
        BigDecimal interestAccruing   = statsRepository.findTotalInterestAccruing(groupId);
        BigDecimal finesCollected     = statsRepository.findTotalFinesCollected(groupId);
        BigDecimal totalSavings       = statsRepository.findTotalSavingsAllTime(groupId);
        BigDecimal outstandingLoans   = statsRepository.findTotalOutstandingLoans(groupId);
        int        totalShares        = statsRepository.findTotalShares(groupId);

        GroupStatsRepository.CycleCounts counts =
                statsRepository.findCycleCounts(groupId, year);

        // Business logic — belongs in use case, not in controller or adapter
        BigDecimal totalInterestEarned = interestCollected.add(interestAccruing);
        BigDecimal distributablePool   = totalSavings
                .add(totalInterestEarned)
                .add(finesCollected);
        BigDecimal dividendPerShare    = totalShares > 0
                ? distributablePool.divide(BigDecimal.valueOf(totalShares), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new YearSummaryResult(
                year,
                counts.total(), counts.closed(),
                totalContributions, totalSavings,
                interestCollected, interestAccruing, totalInterestEarned,
                finesCollected, outstandingLoans,
                distributablePool, totalShares, dividendPerShare);
    }
}