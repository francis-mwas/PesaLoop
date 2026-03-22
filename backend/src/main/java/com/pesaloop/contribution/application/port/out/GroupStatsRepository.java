package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Output port — raw aggregated stats queries for group financial reporting.
 * All methods are read-only and return plain data records.
 * No business logic here — computation (e.g. dividendPerShare) belongs in the use case.
 */
public interface GroupStatsRepository {

    /** Sum of paid_amount across all contribution_entries for a given year. */
    BigDecimal findTotalContributionsByYear(UUID groupId, int year);

    /** Sum of total_interest_repaid across all loan_accounts (all time). */
    BigDecimal findTotalInterestCollected(UUID groupId);

    /** Sum of accrued_interest on ACTIVE loans. */
    BigDecimal findTotalInterestAccruing(UUID groupId);

    /** Sum of fines_balance across active members. */
    BigDecimal findTotalFinesCollected(UUID groupId);

    /** Sum of shares_owned for ACTIVE members. */
    int findTotalShares(UUID groupId);

    /** Sum of paid_amount across ALL contribution_entries (all years — true savings). */
    BigDecimal findTotalSavingsAllTime(UUID groupId);

    /** Sum of outstanding balance (principal_balance + accrued_interest + penalty_balance)
     *  for ACTIVE and OVERDUE loans. */
    BigDecimal findTotalOutstandingLoans(UUID groupId);

    /** Total and closed cycle counts for a given year. */
    CycleCounts findCycleCounts(UUID groupId, int year);

    /** Per-member YTD contributions for a given year (including open cycles). */
    List<MemberYtdRow> findMemberYtdContributions(UUID groupId, int year);

    /** Total accrued interest on ACTIVE loans (for group-stats banner). */
    BigDecimal findTotalAccruedInterest(UUID groupId);

    record CycleCounts(int total, int closed) {}

    record MemberYtdRow(
            UUID memberId,
            String memberNumber,
            String fullName,
            BigDecimal ytdPaid,
            BigDecimal ytdExpected
    ) {}
}