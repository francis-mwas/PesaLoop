package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.ContributionCycleManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Secondary adapter — contribution cycle management queries. */
@Repository
@RequiredArgsConstructor
public class ContributionCycleManagementAdapter implements ContributionCycleManagementRepository {

    private final JdbcTemplate jdbc;

    @Override
    public List<CycleSummaryRow> findCyclesByGroup(UUID groupId) {
        return jdbc.query(
                """
                SELECT cc.id, cc.cycle_number, cc.financial_year, cc.due_date, cc.grace_period_end,
                       cc.status, cc.total_expected_amount,
                       COALESCE(SUM(ce.paid_amount), cc.total_collected_amount) AS total_collected_amount,
                       cc.mgr_beneficiary_id, cc.mgr_payout_amount, cc.mgr_paid_out_at
                  FROM contribution_cycles cc
                  LEFT JOIN contribution_entries ce ON ce.cycle_id = cc.id
                 WHERE cc.group_id = ?
                 GROUP BY cc.id, cc.cycle_number, cc.financial_year, cc.due_date,
                          cc.grace_period_end, cc.status, cc.total_expected_amount,
                          cc.mgr_beneficiary_id, cc.mgr_payout_amount, cc.mgr_paid_out_at
                 ORDER BY cc.financial_year DESC, cc.cycle_number DESC
                """,
                (rs, row) -> new CycleSummaryRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getInt("cycle_number"),
                        rs.getInt("financial_year"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getObject("grace_period_end", LocalDate.class),
                        rs.getString("status"),
                        rs.getBigDecimal("total_expected_amount"),
                        rs.getBigDecimal("total_collected_amount"),
                        rs.getString("mgr_beneficiary_id") != null
                                ? UUID.fromString(rs.getString("mgr_beneficiary_id")) : null,
                        rs.getBigDecimal("mgr_payout_amount"),
                        rs.getTimestamp("mgr_paid_out_at") != null
                                ? rs.getTimestamp("mgr_paid_out_at").toInstant() : null),
                groupId);
    }

    @Override
    public List<CycleSummaryRow> findCyclesByGroupAndYear(UUID groupId, int year) {
        return jdbc.query(
                """
                SELECT cc.id, cc.cycle_number, cc.financial_year, cc.due_date, cc.grace_period_end,
                       cc.status, cc.total_expected_amount,
                       COALESCE(SUM(ce.paid_amount), cc.total_collected_amount) AS total_collected_amount,
                       cc.mgr_beneficiary_id, cc.mgr_payout_amount, cc.mgr_paid_out_at
                  FROM contribution_cycles cc
                  LEFT JOIN contribution_entries ce ON ce.cycle_id = cc.id
                 WHERE cc.group_id = ? AND cc.financial_year = ?
                 GROUP BY cc.id, cc.cycle_number, cc.financial_year, cc.due_date,
                          cc.grace_period_end, cc.status, cc.total_expected_amount,
                          cc.mgr_beneficiary_id, cc.mgr_payout_amount, cc.mgr_paid_out_at
                 ORDER BY cc.cycle_number ASC
                """,
                (rs, row) -> new CycleSummaryRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getInt("cycle_number"),
                        rs.getInt("financial_year"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getObject("grace_period_end", LocalDate.class),
                        rs.getString("status"),
                        rs.getBigDecimal("total_expected_amount"),
                        rs.getBigDecimal("total_collected_amount"),
                        rs.getString("mgr_beneficiary_id") != null
                                ? UUID.fromString(rs.getString("mgr_beneficiary_id")) : null,
                        rs.getBigDecimal("mgr_payout_amount"),
                        rs.getTimestamp("mgr_paid_out_at") != null
                                ? rs.getTimestamp("mgr_paid_out_at").toInstant() : null),
                groupId, year);
    }

    @Override
    public void setMgrBeneficiary(UUID cycleId, UUID groupId,
                                  UUID memberId, BigDecimal payoutAmount) {
        int updated = jdbc.update(
                """
                UPDATE contribution_cycles
                   SET mgr_beneficiary_id = ?, mgr_payout_amount = ?, updated_at = NOW()
                 WHERE id = ? AND group_id = ?
                """,
                memberId, payoutAmount, cycleId, groupId);
        if (updated == 0) throw new IllegalArgumentException("Cycle not found: " + cycleId);
    }

    @Override
    public String findMemberFullName(UUID memberId, UUID groupId) {
        try {
            return jdbc.queryForObject(
                    "SELECT u.full_name FROM users u JOIN members m ON m.user_id=u.id WHERE m.id=? AND m.group_id=?",
                    String.class, memberId, groupId);
        } catch (Exception e) { return "Unknown member"; }
    }

    @Override
    @Transactional
    public OpenCycleResult openCycle(UUID groupId, LocalDate dueDate,
                                     int graceDays, UUID mgrBeneficiaryId) {
        int year = LocalDate.now().getYear();

        Integer lastCycle = jdbc.queryForObject(
                "SELECT COALESCE(MAX(cycle_number),0) FROM contribution_cycles WHERE group_id=? AND financial_year=?",
                Integer.class, groupId, year);
        int cycleNumber = (lastCycle != null ? lastCycle : 0) + 1;
        LocalDate gracePeriodEnd = dueDate.plusDays(graceDays);

        BigDecimal totalExpected = jdbc.queryForObject(
                "SELECT COALESCE(SUM(m.shares_owned * g.share_price_amount),0) FROM members m JOIN groups g ON g.id=m.group_id WHERE m.group_id=? AND m.status='ACTIVE'",
                BigDecimal.class, groupId);

        Integer memberCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM members WHERE group_id=? AND status='ACTIVE'",
                Integer.class, groupId);

        UUID cycleId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO contribution_cycles
                    (id, group_id, cycle_number, financial_year, due_date, grace_period_end,
                     status, total_expected_amount, mgr_beneficiary_id, created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,'OPEN',?,?,NOW(),NOW(),0)
                ON CONFLICT (group_id, cycle_number, financial_year) DO NOTHING
                """,
                cycleId, groupId, cycleNumber, year, dueDate, gracePeriodEnd,
                totalExpected, mgrBeneficiaryId);

        jdbc.update(
                """
                INSERT INTO contribution_entries
                    (id, group_id, cycle_id, member_id, expected_amount, paid_amount,
                     currency_code, status, created_by, created_at, updated_at, version)
                SELECT gen_random_uuid(), m.group_id, ?, m.id,
                       m.shares_owned * g.share_price_amount, 0,
                       'KES', 'PENDING', m.id, NOW(), NOW(), 0
                  FROM members m JOIN groups g ON g.id=m.group_id
                 WHERE m.group_id=? AND m.status='ACTIVE'
                """,
                cycleId, groupId);

        return new OpenCycleResult(cycleId, cycleNumber, year, dueDate, gracePeriodEnd,
                totalExpected, memberCount != null ? memberCount : 0);
    }
}