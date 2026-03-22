package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.MemberStatementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Secondary adapter — member statement and loan book queries. */
@Repository
@RequiredArgsConstructor
public class MemberStatementJdbcAdapter implements MemberStatementRepository {

    private final JdbcTemplate jdbc;

    @Override
    public MemberProfile findMemberProfile(UUID memberId, UUID groupId) {
        return jdbc.queryForObject(
                """
                SELECT m.member_number, u.full_name, u.phone_number,
                       m.shares_owned, g.share_price_amount,
                       COALESCE(SUM(ce.paid_amount), m.savings_balance) AS savings_balance,
                       m.arrears_balance, m.fines_balance, m.joined_on
                  FROM members m
                  JOIN users u ON u.id = m.user_id
                  JOIN groups g ON g.id = m.group_id
             LEFT JOIN contribution_entries ce ON ce.member_id = m.id AND ce.group_id = m.group_id
                 WHERE m.id = ? AND m.group_id = ?
                 GROUP BY m.member_number, u.full_name, u.phone_number,
                          m.shares_owned, g.share_price_amount,
                          m.savings_balance, m.arrears_balance, m.fines_balance, m.joined_on
                """,
                (rs, row) -> new MemberProfile(
                        rs.getString("member_number"), rs.getString("full_name"),
                        rs.getString("phone_number"), rs.getInt("shares_owned"),
                        rs.getBigDecimal("share_price_amount"), rs.getBigDecimal("savings_balance"),
                        rs.getBigDecimal("arrears_balance"), rs.getBigDecimal("fines_balance"),
                        rs.getObject("joined_on", LocalDate.class)),
                memberId, groupId);
    }

    @Override
    public List<ContributionLine> findContributions(UUID memberId, UUID groupId) {
        return jdbc.query(
                """
                SELECT cc.cycle_number, cc.financial_year, cc.due_date,
                       ce.expected_amount, ce.paid_amount, ce.status,
                       ce.first_payment_at, ce.fully_paid_at
                  FROM contribution_entries ce
                  JOIN contribution_cycles cc ON cc.id = ce.cycle_id
                 WHERE ce.member_id = ? AND ce.group_id = ?
                 ORDER BY cc.financial_year, cc.cycle_number
                """,
                (rs, row) -> new ContributionLine(
                        rs.getInt("cycle_number"), rs.getInt("financial_year"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBigDecimal("expected_amount"), rs.getBigDecimal("paid_amount"),
                        rs.getString("status"),
                        // Use fully_paid_at for PAID, first_payment_at for PARTIAL
                        rs.getTimestamp("fully_paid_at") != null
                                ? rs.getTimestamp("fully_paid_at").toInstant()
                                : rs.getTimestamp("first_payment_at") != null
                                ? rs.getTimestamp("first_payment_at").toInstant()
                                : null),
                memberId, groupId);
    }

    @Override
    public List<LoanLine> findLoans(UUID memberId, UUID groupId) {
        return jdbc.query(
                """
                SELECT la.loan_reference, la.status, lp.name AS product_name,
                       la.principal_amount, la.total_interest_charged,
                       la.principal_balance, la.accrued_interest,
                       la.total_principal_repaid, la.total_interest_repaid,
                       la.disbursement_date, la.due_date, la.settled_at
                  FROM loan_accounts la
                  JOIN loan_products lp ON lp.id = la.product_id
                 WHERE la.member_id = ? AND la.group_id = ?
                 ORDER BY la.created_at
                """,
                (rs, row) -> new LoanLine(
                        rs.getString("loan_reference"), rs.getString("status"),
                        rs.getString("product_name"), rs.getBigDecimal("principal_amount"),
                        rs.getBigDecimal("total_interest_charged"),
                        rs.getBigDecimal("principal_balance"), rs.getBigDecimal("accrued_interest"),
                        rs.getBigDecimal("total_principal_repaid"),
                        rs.getBigDecimal("total_interest_repaid"),
                        rs.getObject("disbursement_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getTimestamp("settled_at") != null
                                ? rs.getTimestamp("settled_at").toInstant() : null),
                memberId, groupId);
    }

    @Override
    public List<RepaymentLine> findRepayments(UUID memberId, UUID groupId) {
        return jdbc.query(
                """
                SELECT pr.amount, pr.payment_method, pr.mpesa_reference,
                       pr.narration, pr.recorded_at, la.loan_reference
                  FROM payment_records pr
                  JOIN loan_accounts la ON la.id = pr.loan_id
                 WHERE pr.member_id = ? AND pr.group_id = ?
                   AND pr.payment_type = 'LOAN_REPAYMENT'
                 ORDER BY pr.recorded_at
                """,
                (rs, row) -> new RepaymentLine(
                        rs.getBigDecimal("amount"), rs.getString("payment_method"),
                        rs.getString("mpesa_reference"), rs.getString("narration"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getString("loan_reference")),
                memberId, groupId);
    }

    @Override
    public List<LoanBookEntry> findActiveLoanBook(UUID groupId) {
        return jdbc.query(
                """
                SELECT loan_id, loan_reference, status,
                       member_id, member_number, member_name, member_phone,
                       product_name, interest_type,
                       principal_amount, principal_balance,
                       accrued_interest, penalty_balance, total_outstanding,
                       disbursement_date, due_date, is_overdue
                  FROM v_loan_book
                 WHERE group_id = ?
                   AND status IN ('ACTIVE','PENDING_DISBURSEMENT','DEFAULTED')
                 ORDER BY is_overdue DESC, due_date
                """,
                (rs, row) -> new LoanBookEntry(
                        UUID.fromString(rs.getString("loan_id")),
                        rs.getString("loan_reference"), rs.getString("status"),
                        UUID.fromString(rs.getString("member_id")),
                        rs.getString("member_number"), rs.getString("member_name"),
                        rs.getString("member_phone"), rs.getString("product_name"),
                        rs.getString("interest_type"), rs.getBigDecimal("principal_amount"),
                        rs.getBigDecimal("principal_balance"), rs.getBigDecimal("accrued_interest"),
                        rs.getBigDecimal("penalty_balance"), rs.getBigDecimal("total_outstanding"),
                        rs.getObject("disbursement_date", LocalDate.class),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBoolean("is_overdue")),
                groupId);
    }
}