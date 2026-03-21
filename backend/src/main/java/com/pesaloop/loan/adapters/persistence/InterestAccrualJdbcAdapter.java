package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.InterestAccrualRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Secondary adapter — loan interest accrual persistence. */
@Repository
@RequiredArgsConstructor
public class InterestAccrualJdbcAdapter implements InterestAccrualRepository {

    private final JdbcTemplate jdbc;

    @Override
    public List<LoanAccrualRow> findActiveAccrualLoans() {
        return jdbc.query(
                """
                SELECT la.id, la.group_id, la.principal_balance, la.accrued_interest,
                       la.disbursement_date,
                       lp.interest_type, lp.accrual_frequency, lp.interest_rate,
                       lp.custom_accrual_interval_days
                  FROM loan_accounts la
                  JOIN loan_products lp ON lp.id = la.product_id
                 WHERE la.status = 'ACTIVE'
                   AND lp.accrual_frequency != 'FLAT_RATE'
                   AND la.principal_balance > 0
                 ORDER BY la.id
                """,
                (rs, row) -> new LoanAccrualRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("group_id")),
                        rs.getBigDecimal("principal_balance"),
                        rs.getBigDecimal("accrued_interest"),
                        rs.getObject("disbursement_date", LocalDate.class),
                        rs.getString("interest_type"),
                        rs.getString("accrual_frequency"),
                        rs.getBigDecimal("interest_rate"),
                        rs.getObject("custom_accrual_interval_days", Integer.class)));
    }

    @Override
    @Transactional
    public void recordAccruedInterest(UUID loanId, UUID groupId,
                                       BigDecimal interestAmount,
                                       LocalDate accrualDate, String accrualFrequency) {
        jdbc.update(
                "UPDATE loan_accounts SET accrued_interest = accrued_interest + ?, updated_at = NOW() WHERE id = ?",
                interestAmount, loanId);

        jdbc.update(
                """
                UPDATE repayment_installments
                   SET interest_due = interest_due + ?,
                       total_due = total_due + ?
                 WHERE loan_id = ?
                   AND status IN ('PENDING','PARTIAL')
                   AND installment_number = (
                       SELECT MIN(installment_number) FROM repayment_installments
                        WHERE loan_id = ? AND status IN ('PENDING','PARTIAL')
                   )
                """,
                interestAmount, interestAmount, loanId, loanId);

        jdbc.update(
                """
                INSERT INTO audit_log (group_id, entity_type, entity_id, action, after_state)
                VALUES (?, 'LoanAccount', ?, 'INTEREST_ACCRUED',
                        jsonb_build_object('amount', ?, 'date', ?, 'frequency', ?))
                """,
                groupId, loanId, interestAmount.toPlainString(),
                accrualDate.toString(), accrualFrequency);
    }
}
