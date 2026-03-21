package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.ManualPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Secondary adapter — manual payment persistence across contribution and loan tables. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ManualPaymentJdbcAdapter implements ManualPaymentRepository {

    private final JdbcTemplate jdbc;

    @Override
    public MemberInfo findActiveMember(UUID memberId, UUID groupId) {
        return jdbc.queryForObject(
                "SELECT m.id,m.member_number,m.status,u.full_name FROM members m JOIN users u ON u.id=m.user_id WHERE m.id=? AND m.group_id=?",
                (rs, r) -> new MemberInfo(UUID.fromString(rs.getString("id")),
                        rs.getString("member_number"), rs.getString("status"), rs.getString("full_name")),
                memberId, groupId);
    }

    @Override
    public UUID findCurrentOpenCycleId(UUID groupId, UUID memberId) {
        try {
            return jdbc.queryForObject(
                    """
                    SELECT e.cycle_id FROM contribution_entries e
                      JOIN contribution_cycles c ON c.id=e.cycle_id
                     WHERE e.group_id=? AND e.member_id=?
                       AND c.status IN ('OPEN','GRACE_PERIOD')
                       AND e.status IN ('PENDING','PARTIAL')
                     ORDER BY c.due_date DESC LIMIT 1
                    """,
                    (rs, r) -> UUID.fromString(rs.getString("cycle_id")),
                    groupId, memberId);
        } catch (Exception e) { return null; }
    }

    @Override
    public int validateAndGetCycleNumber(UUID cycleId, UUID groupId) {
        String status = jdbc.queryForObject(
                "SELECT status FROM contribution_cycles WHERE id=? AND group_id=?",
                String.class, cycleId, groupId);
        if (!"OPEN".equals(status) && !"GRACE_PERIOD".equals(status))
            throw new IllegalStateException("Cycle is not open (status: " + status + ")");
        Integer num = jdbc.queryForObject(
                "SELECT cycle_number FROM contribution_cycles WHERE id=?", Integer.class, cycleId);
        return num != null ? num : 0;
    }

    @Override
    @Transactional
    public void applyContributionPayment(UUID cycleId, UUID memberId, BigDecimal amount,
                                          String methodName, String reference, UUID recordedBy) {
        jdbc.update(
                """
                UPDATE contribution_entries
                   SET paid_amount=paid_amount+?, last_payment_method=?,
                       last_mpesa_reference=?, recorded_by=?,
                       first_payment_at=COALESCE(first_payment_at,NOW()),
                       fully_paid_at=CASE WHEN paid_amount+?>=expected_amount THEN NOW() ELSE fully_paid_at END,
                       status=CASE WHEN paid_amount+?>=expected_amount THEN 'PAID' ELSE 'PARTIAL' END,
                       updated_at=NOW()
                 WHERE cycle_id=? AND member_id=?
                """,
                amount, methodName, reference, recordedBy,
                amount, amount, cycleId, memberId);
        jdbc.update("UPDATE contribution_cycles SET total_collected_amount=total_collected_amount+? WHERE id=?",
                amount, cycleId);
        jdbc.update("UPDATE members SET savings_balance=savings_balance+? WHERE id=?", amount, memberId);
    }

    @Override
    public LoanInfo findActiveLoan(UUID loanId, UUID memberId, UUID groupId) {
        return jdbc.queryForObject(
                "SELECT id,loan_reference,status,principal_balance,accrued_interest,penalty_balance FROM loan_accounts WHERE id=? AND member_id=? AND group_id=?",
                (rs, r) -> new LoanInfo(UUID.fromString(rs.getString("id")),
                        rs.getString("loan_reference"), rs.getString("status"),
                        rs.getBigDecimal("principal_balance"), rs.getBigDecimal("accrued_interest"),
                        rs.getBigDecimal("penalty_balance")),
                loanId, memberId, groupId);
    }

    @Override
    @Transactional
    public void applyLoanRepayment(UUID loanId, BigDecimal toPrincipal, BigDecimal toInterest,
                                    BigDecimal toPenalty, boolean fullySettled) {
        jdbc.update(
                """
                UPDATE loan_accounts
                   SET principal_balance=principal_balance-?,
                       accrued_interest=accrued_interest-?,
                       penalty_balance=penalty_balance-?,
                       total_principal_repaid=total_principal_repaid+?,
                       total_interest_repaid=total_interest_repaid+?,
                       status=CASE WHEN ? THEN 'SETTLED' ELSE status END,
                       settled_at=CASE WHEN ? THEN NOW() ELSE settled_at END,
                       updated_at=NOW()
                 WHERE id=?
                """,
                toPrincipal, toInterest, toPenalty,
                toPrincipal, toInterest,
                fullySettled, fullySettled, loanId);
    }

    @Override
    @Transactional
    public void applyRepaymentToInstallment(UUID loanId, BigDecimal toPrincipal,
                                             BigDecimal toInterest, BigDecimal toPenalty) {
        jdbc.update(
                """
                UPDATE repayment_installments
                   SET principal_paid=principal_paid+?,
                       interest_paid=interest_paid+?,
                       penalty_paid=penalty_paid+?,
                       status=CASE WHEN principal_paid+?>=principal_due AND interest_paid+?>=interest_due THEN 'PAID' ELSE 'PARTIAL' END,
                       paid_at=CASE WHEN principal_paid+?>=principal_due AND interest_paid+?>=interest_due THEN NOW() ELSE paid_at END
                 WHERE loan_id=?
                   AND installment_number=(SELECT MIN(installment_number) FROM repayment_installments WHERE loan_id=? AND status IN ('PENDING','PARTIAL','OVERDUE'))
                """,
                toPrincipal, toInterest, toPenalty,
                toPrincipal, toInterest, toPrincipal, toInterest,
                loanId, loanId);
    }

    @Override
    public void releaseGuarantors(UUID loanId) {
        jdbc.update("UPDATE loan_guarantors SET status='RELEASED' WHERE loan_id=?", loanId);
    }

    @Override
    @Transactional
    public UUID recordPayment(UUID groupId, UUID memberId, UUID cycleId, UUID loanId,
                               String paymentType, BigDecimal amount, String methodName,
                               String reference, String notes, UUID recordedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO payment_records
                    (id,group_id,entry_id,loan_id,member_id,payment_type,
                     amount,currency_code,payment_method,mpesa_reference,
                     narration,status,recorded_by,recorded_at)
                VALUES(?,?,?,?,?,?,?,'KES',?,?,?,'COMPLETED',?,NOW())
                """,
                id, groupId, cycleId, loanId, memberId, paymentType,
                amount, methodName, reference, notes, recordedBy);
        jdbc.update(
                """
                INSERT INTO audit_log(group_id,actor_id,entity_type,entity_id,action,after_state)
                VALUES(?,?,'PaymentRecord',?,'MANUAL_PAYMENT_RECORDED',
                       jsonb_build_object('amount',?,'method',?,'reference',?,'target',?,'notes',?))
                """,
                groupId, recordedBy, id, amount, methodName, reference, paymentType, notes);
        return id;
    }
}
