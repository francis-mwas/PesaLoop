package com.pesaloop.contribution.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/** Input port — fetches year-end earnings summary for dashboard and reports. */
public interface GetYearSummaryPort {
    YearSummaryResult execute(UUID groupId, int year);

    record YearSummaryResult(
            int year,
            int totalCycles,
            int closedCycles,
            BigDecimal totalContributions,
            BigDecimal totalSavings,
            BigDecimal interestCollected,
            BigDecimal interestAccruing,
            BigDecimal totalInterestEarned,
            BigDecimal finesCollected,
            BigDecimal outstandingLoans,
            BigDecimal distributablePool,
            int totalShares,
            BigDecimal dividendPerShare
    ) {}
}