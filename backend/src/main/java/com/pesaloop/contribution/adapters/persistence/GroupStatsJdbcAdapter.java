package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.GroupStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Secondary adapter — implements GroupStatsRepository using JDBC.
 * All queries are read-only aggregations. No domain logic here.
 */
@Repository
@RequiredArgsConstructor
public class GroupStatsJdbcAdapter implements GroupStatsRepository {

    private final JdbcTemplate jdbc;

    @Override
    public BigDecimal findTotalContributionsByYear(UUID groupId, int year) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(ce.paid_amount), 0)
                  FROM contribution_entries ce
                  JOIN contribution_cycles cc ON cc.id = ce.cycle_id
                 WHERE cc.group_id = ? AND cc.financial_year = ?
                """,
                BigDecimal.class, groupId, year);
    }

    @Override
    public BigDecimal findTotalInterestCollected(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_interest_repaid), 0) FROM loan_accounts WHERE group_id = ?",
                BigDecimal.class, groupId);
    }

    @Override
    public BigDecimal findTotalInterestAccruing(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(accrued_interest), 0) FROM loan_accounts WHERE group_id = ? AND status = 'ACTIVE'",
                BigDecimal.class, groupId);
    }

    @Override
    public BigDecimal findTotalAccruedInterest(UUID groupId) {
        return findTotalInterestAccruing(groupId);
    }

    @Override
    public BigDecimal findTotalFinesCollected(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(fines_balance), 0) FROM members WHERE group_id = ?",
                BigDecimal.class, groupId);
    }

    @Override
    public int findTotalShares(UUID groupId) {
        Integer result = jdbc.queryForObject(
                "SELECT COALESCE(SUM(shares_owned), 0) FROM members WHERE group_id = ? AND status = 'ACTIVE'",
                Integer.class, groupId);
        return result != null ? result : 0;
    }

    @Override
    public BigDecimal findTotalSavingsAllTime(UUID groupId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(ce.paid_amount), 0)
                  FROM contribution_entries ce
                  JOIN members m ON m.id = ce.member_id
                 WHERE m.group_id = ?
                """,
                BigDecimal.class, groupId);
    }

    @Override
    public BigDecimal findTotalOutstandingLoans(UUID groupId) {
        return jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(principal_balance + accrued_interest + penalty_balance), 0)
                  FROM loan_accounts
                 WHERE group_id = ? AND status IN ('ACTIVE', 'OVERDUE')
                """,
                BigDecimal.class, groupId);
    }

    @Override
    public CycleCounts findCycleCounts(UUID groupId, int year) {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contribution_cycles WHERE group_id = ? AND financial_year = ?",
                Integer.class, groupId, year);
        Integer closed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contribution_cycles WHERE group_id = ? AND financial_year = ? AND status = 'CLOSED'",
                Integer.class, groupId, year);
        return new CycleCounts(
                total  != null ? total  : 0,
                closed != null ? closed : 0);
    }

    @Override
    public List<MemberYtdRow> findMemberYtdContributions(UUID groupId, int year) {
        return jdbc.query(
                """
                SELECT m.id AS member_id, m.member_number, u.full_name,
                       COALESCE(SUM(ce.paid_amount),    0) AS ytd_paid,
                       COALESCE(SUM(ce.expected_amount),0) AS ytd_expected
                  FROM members m
                  JOIN users u ON u.id = m.user_id
             LEFT JOIN contribution_entries ce
                    ON ce.member_id = m.id AND ce.group_id = m.group_id
             LEFT JOIN contribution_cycles cc
                    ON cc.id = ce.cycle_id
                   AND cc.financial_year = ? AND cc.group_id = ?
                 WHERE m.group_id = ? AND m.status = 'ACTIVE'
                 GROUP BY m.id, m.member_number, u.full_name
                 ORDER BY m.member_number
                """,
                (rs, row) -> new MemberYtdRow(
                        UUID.fromString(rs.getString("member_id")),
                        rs.getString("member_number"),
                        rs.getString("full_name"),
                        rs.getBigDecimal("ytd_paid"),
                        rs.getBigDecimal("ytd_expected")),
                year, groupId, groupId);
    }
}