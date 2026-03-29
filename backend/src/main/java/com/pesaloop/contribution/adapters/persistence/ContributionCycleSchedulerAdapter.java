package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.ContributionCycleSchedulerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Secondary adapter — implements ContributionCycleSchedulerRepository using JDBC.
 * All scheduler SQL lives here, not in the scheduler itself.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ContributionCycleSchedulerAdapter implements ContributionCycleSchedulerRepository {

    private final JdbcTemplate jdbc;

    // ── 1. Cycle generation ───────────────────────────────────────────────────

    @Override
    public List<GroupCycleInfo> findActiveGroupsForCycleGeneration(int currentYear) {
        return jdbc.query(
                """
                SELECT g.id, g.currency_code, g.contribution_frequency,
                       g.custom_frequency_days, g.grace_period_days,
                       g.financial_year_start_month,
                       COALESCE(MAX(cc.cycle_number), 0)    AS last_cycle_number,
                       COALESCE(MAX(cc.financial_year), ?)  AS last_financial_year
                  FROM groups g
                  LEFT JOIN contribution_cycles cc ON cc.group_id = g.id
                 WHERE g.status = 'ACTIVE'
                 GROUP BY g.id, g.currency_code, g.contribution_frequency,
                          g.custom_frequency_days, g.grace_period_days,
                          g.financial_year_start_month
                """,
                (rs, row) -> new GroupCycleInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("currency_code"),
                        rs.getString("contribution_frequency"),
                        rs.getObject("custom_frequency_days", Integer.class),
                        rs.getInt("grace_period_days"),
                        rs.getInt("financial_year_start_month"),
                        rs.getInt("last_cycle_number"),
                        rs.getInt("last_financial_year")),
                currentYear);
    }

    @Override
    @Transactional
    public UUID createCycleWithEntries(UUID groupId, int cycleNumber, int financialYear,
                                       LocalDate dueDate, LocalDate gracePeriodEnd,
                                       String currencyCode) {
        // Calculate total expected (snapshot at cycle creation)
        BigDecimal totalExpected = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(
                    CASE WHEN m.custom_contribution_amount IS NOT NULL
                         THEN m.custom_contribution_amount
                         ELSE g.share_price_amount * m.shares_owned
                    END), 0)
                  FROM members m
                  JOIN groups g ON g.id = m.group_id
                 WHERE m.group_id = ? AND m.status = 'ACTIVE'
                """,
                BigDecimal.class, groupId);

        UUID cycleId = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO contribution_cycles
                    (id, group_id, cycle_number, financial_year, due_date, grace_period_end,
                     status, total_expected_amount, currency_code, created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,'OPEN',?,?,NOW(),NOW(),0)
                """,
                cycleId, groupId, cycleNumber, financialYear,
                dueDate, gracePeriodEnd,
                totalExpected != null ? totalExpected : BigDecimal.ZERO,
                currencyCode);

        jdbc.update(
                """
                INSERT INTO contribution_entries
                    (id, group_id, cycle_id, member_id, expected_amount, paid_amount,
                     currency_code, status, created_by, created_at, updated_at, version)
                SELECT gen_random_uuid(), m.group_id, ?, m.id,
                       CASE WHEN m.custom_contribution_amount IS NOT NULL
                            THEN m.custom_contribution_amount
                            ELSE g.share_price_amount * m.shares_owned
                       END,
                       0, g.currency_code, 'PENDING', m.user_id, NOW(), NOW(), 0
                  FROM members m
                  JOIN groups g ON g.id = m.group_id
                 WHERE m.group_id = ? AND m.status = 'ACTIVE'
                """,
                cycleId, groupId);

        return cycleId;
    }

    // ── 2. Cycle transitions ──────────────────────────────────────────────────

    @Override
    public int transitionOpenToGrace(LocalDate today) {
        return jdbc.update(
                "UPDATE contribution_cycles SET status='GRACE_PERIOD', updated_at=NOW() WHERE status='OPEN' AND due_date<?",
                today);
    }

    @Override
    public List<UUID> findCyclesReadyToClose(LocalDate today) {
        return jdbc.queryForList(
                "SELECT id FROM contribution_cycles WHERE status='GRACE_PERIOD' AND grace_period_end<?",
                UUID.class, today);
    }

    @Override
    @Transactional
    public void closeCycleAndMoveArrears(UUID cycleId, LocalDate today) {
        // Add unpaid balance to member arrears
        jdbc.update(
                """
                UPDATE members m
                   SET arrears_balance = arrears_balance + (
                       SELECT expected_amount - paid_amount
                         FROM contribution_entries e
                        WHERE e.cycle_id = ? AND e.member_id = m.id
                          AND e.status IN ('PENDING','PARTIAL')
                   ),
                   updated_at = NOW()
                 WHERE id IN (
                     SELECT member_id FROM contribution_entries
                      WHERE cycle_id = ? AND status IN ('PENDING','PARTIAL')
                 )
                """,
                cycleId, cycleId);

        // Mark those entries as ARREARS_APPLIED
        jdbc.update(
                """
                UPDATE contribution_entries
                   SET status = 'ARREARS_APPLIED',
                       arrears_carried_forward = expected_amount - paid_amount,
                       updated_at = NOW()
                 WHERE cycle_id = ? AND status IN ('PENDING','PARTIAL')
                """,
                cycleId);

        // Close the cycle
        jdbc.update(
                "UPDATE contribution_cycles SET status='CLOSED', updated_at=NOW() WHERE id=?",
                cycleId);

        log.info("Cycle closed: id={} date={}", cycleId, today);
    }

    @Override
    public int markOverdueInstallments(LocalDate today) {
        return jdbc.update(
                "UPDATE repayment_installments SET status='OVERDUE' WHERE status='PENDING' AND due_date<?",
                today);
    }

    @Override
    public void applyLoanPenalties(LocalDate today) {
        jdbc.update(
                """
                UPDATE loan_accounts la
                   SET penalty_balance = penalty_balance + (
                       SELECT ri.total_due * lp.late_repayment_penalty_rate
                         FROM repayment_installments ri
                         JOIN loan_products lp ON lp.id = la.product_id
                        WHERE ri.loan_id = la.id
                          AND ri.status = 'OVERDUE'
                          AND ri.due_date + (lp.penalty_grace_period_days || ' days')::INTERVAL = ?
                        LIMIT 1
                   ),
                   updated_at = NOW()
                 WHERE la.status = 'ACTIVE'
                   AND la.penalty_balance >= 0
                """,
                today);
    }

    // ── 3. Reminders ──────────────────────────────────────────────────────────

    @Override
    public List<ReminderRow> findMembersNeedingReminders(LocalDate dueBefore) {
        return jdbc.query(
                """
                SELECT m.phone_number, m.id AS member_id, g.id AS group_id,
                       g.name AS group_name, e.expected_amount, c.due_date,
                       sw.balance_units
                  FROM contribution_entries e
                  JOIN contribution_cycles c  ON c.id = e.cycle_id
                  JOIN members m              ON m.id = e.member_id
                  JOIN groups g               ON g.id = e.group_id
                  JOIN sms_wallets sw         ON sw.group_id = g.id
                 WHERE c.status IN ('OPEN','GRACE_PERIOD')
                   AND e.status IN ('PENDING','PARTIAL')
                   AND c.due_date <= ?
                   AND m.phone_number IS NOT NULL
                   AND sw.balance_units > 0
                 ORDER BY g.id, m.id
                """,
                (rs, row) -> new ReminderRow(
                        rs.getString("phone_number"),
                        UUID.fromString(rs.getString("member_id")),
                        UUID.fromString(rs.getString("group_id")),
                        rs.getString("group_name"),
                        rs.getBigDecimal("expected_amount"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getInt("balance_units")),
                dueBefore);
    }
}