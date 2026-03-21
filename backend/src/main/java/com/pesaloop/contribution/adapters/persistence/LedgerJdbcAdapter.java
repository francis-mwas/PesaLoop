package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.LedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LedgerJdbcAdapter implements LedgerRepository {

    private final JdbcTemplate jdbc;

    @Override
    public List<MemberLedgerRow> findMonthlyLedger(UUID groupId,
                                                    LocalDate monthStart,
                                                    LocalDate monthEnd) {
        return jdbc.query(
                """
                SELECT
                    m.id                AS member_id,
                    m.member_number,
                    u.full_name         AS member_name,
                    m.shares_owned,
                    g.share_price_amount,
                    COALESCE(ce.paid_amount, 0)            AS monthly_savings,
                    COALESCE(ce.expected_amount, 0)        AS expected_savings,
                    ce.status                              AS contribution_status,
                    COALESCE(la.principal_balance, 0)      AS loan_outstanding,
                    COALESCE(la.total_interest_charged, 0) AS total_interest_charged,
                    COALESCE(la.accrued_interest, 0)       AS accrued_interest,
                    COALESCE(la.total_interest_repaid, 0)  AS interest_repaid,
                    COALESCE(SUM(pr.amount), 0)            AS monthly_repayment,
                    m.savings_balance                      AS cumulative_savings,
                    m.arrears_balance,
                    m.fines_balance,
                    la.loan_reference,
                    la.status                              AS loan_status,
                    la.disbursement_date,
                    la.due_date                            AS loan_due_date
                FROM members m
                JOIN users u ON u.id = m.user_id
                JOIN groups g ON g.id = m.group_id
                LEFT JOIN contribution_cycles cc
                    ON cc.group_id = m.group_id
                   AND cc.due_date BETWEEN ? AND ?
                LEFT JOIN contribution_entries ce
                    ON ce.member_id = m.id AND ce.cycle_id = cc.id
                LEFT JOIN LATERAL (
                    SELECT *
                      FROM loan_accounts
                     WHERE member_id = m.id
                       AND status IN ('ACTIVE','PENDING_DISBURSEMENT','SETTLED')
                       AND (settled_at IS NULL OR settled_at >= ?)
                     ORDER BY created_at DESC
                     LIMIT 1
                ) la ON TRUE
                LEFT JOIN payment_records pr
                    ON pr.member_id = m.id
                   AND pr.payment_type = 'LOAN_REPAYMENT'
                   AND pr.recorded_at >= ?
                   AND pr.recorded_at < ?
                WHERE m.group_id = ? AND m.status = 'ACTIVE'
                GROUP BY
                    m.id, m.member_number, u.full_name, m.shares_owned,
                    g.share_price_amount, ce.paid_amount, ce.expected_amount, ce.status,
                    la.principal_balance, la.total_interest_charged, la.accrued_interest,
                    la.total_interest_repaid, m.savings_balance, m.arrears_balance,
                    m.fines_balance, la.loan_reference, la.status, la.disbursement_date, la.due_date
                ORDER BY m.member_number
                """,
                (rs, row) -> new MemberLedgerRow(
                        UUID.fromString(rs.getString("member_id")),
                        rs.getString("member_number"),
                        rs.getString("member_name"),
                        rs.getInt("shares_owned"),
                        rs.getBigDecimal("share_price_amount"),
                        rs.getBigDecimal("monthly_savings"),
                        rs.getBigDecimal("expected_savings"),
                        rs.getString("contribution_status"),
                        rs.getBigDecimal("loan_outstanding"),
                        rs.getBigDecimal("total_interest_charged"),
                        rs.getBigDecimal("accrued_interest"),
                        rs.getBigDecimal("interest_repaid"),
                        rs.getBigDecimal("monthly_repayment"),
                        rs.getBigDecimal("cumulative_savings"),
                        rs.getBigDecimal("arrears_balance"),
                        rs.getBigDecimal("fines_balance"),
                        rs.getString("loan_reference"),
                        rs.getString("loan_status"),
                        rs.getObject("disbursement_date", LocalDate.class),
                        rs.getObject("loan_due_date", LocalDate.class)
                ),
                monthStart, monthEnd,
                monthStart,
                monthStart.atStartOfDay().toString(),
                monthEnd.plusDays(1).atStartOfDay().toString(),
                groupId
        );
    }
}
