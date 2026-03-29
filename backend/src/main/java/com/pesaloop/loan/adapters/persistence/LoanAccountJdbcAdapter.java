package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import com.pesaloop.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Secondary adapter — the single implementation of LoanAccountRepository.
 * Delegates simple CRUD to JPA; uses JDBC for state-transition commands,
 * rich joins, and aggregates.
 */
@Repository
@RequiredArgsConstructor
public class LoanAccountJdbcAdapter implements LoanAccountRepository {

    private final JdbcTemplate jdbc;
    private final LoanAccountJpaRepository jpa;
    private final LoanMapper mapper;

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Override
    public LoanAccount save(LoanAccount loan) {
        LoanAccountJpaEntity entity = mapper.toEntity(loan);
        entity.setGroupId(loan.getGroupId());
        entity.setCreatedBy(loan.getMemberId());
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<LoanAccount> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<LoanAccount> findByGroupIdAndReference(UUID groupId, String reference) {
        return jpa.findByGroupIdAndLoanReference(groupId, reference).map(mapper::toDomain);
    }

    @Override
    public List<LoanAccount> findByMemberId(UUID memberId) {
        return jpa.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LoanAccount> findByMemberIdAndStatus(UUID memberId, LoanStatus status) {
        return jpa.findByMemberIdAndStatus(memberId, status)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<LoanAccount> findActiveByGroupId(UUID groupId) {
        return jpa.findActiveByGroupId(groupId, com.pesaloop.loan.domain.model.LoanStatus.ACTIVE)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public int countActiveByMemberIdAndProductId(UUID memberId, UUID productId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loan_accounts WHERE member_id=? AND product_id=? AND status IN ('ACTIVE','APPROVED','PENDING_DISBURSEMENT')",
                Integer.class, memberId, productId);
        return count != null ? count : 0;
    }

    @Override
    public String nextLoanReference(UUID groupId, int year) {
        Integer next = jdbc.queryForObject("SELECT next_loan_number(?,?)", Integer.class, groupId, year);
        return "LN-%d-%04d".formatted(year, next != null ? next : 1);
    }

    // ── State-transition commands ─────────────────────────────────────────────

    @Override
    public void approve(UUID loanId, Money approvedPrincipal, Money totalInterest,
                        LocalDate dueDate, UUID approvedByUserId, String note) {
        jdbc.update(
                """
                UPDATE loan_accounts
                   SET status                 = 'APPROVED',
                       principal_amount       = ?,
                       total_interest_charged = ?,
                       due_date               = ?,
                       approved_by            = ?,
                       approved_at            = NOW(),
                       application_note       = COALESCE(application_note, ?),
                       updated_at             = NOW()
                 WHERE id = ?
                """,
                approvedPrincipal.getAmount(), totalInterest.getAmount(),
                dueDate, approvedByUserId, note, loanId);
    }

    @Override
    public void reject(UUID loanId, String reason, UUID rejectedByUserId) {
        jdbc.update(
                """
                UPDATE loan_accounts
                   SET status           = 'REJECTED',
                       application_note = ?,
                       approved_by      = ?,
                       approved_at      = NOW(),
                       updated_at       = NOW()
                 WHERE id = ?
                """,
                reason, rejectedByUserId, loanId);
        // Release any guarantors
        jdbc.update("UPDATE loan_guarantors SET status='RELEASED', updated_at=NOW() WHERE loan_id=?", loanId);
    }

    @Override
    public void markPendingDisbursement(UUID loanId) {
        jdbc.update(
                "UPDATE loan_accounts SET status='PENDING_DISBURSEMENT', updated_at=NOW() WHERE id=? AND status='APPROVED'",
                loanId);
    }

    @Override
    public void activateAfterDisbursement(UUID loanId, String mpesaRef, UUID confirmedByUserId) {
        jdbc.update(
                """
                UPDATE loan_accounts
                   SET status                   = 'ACTIVE',
                       principal_balance        = principal_amount,
                       disbursement_date        = CURRENT_DATE,
                       disbursement_mpesa_ref   = ?,
                       disbursed_by             = ?,
                       disbursed_at             = NOW(),
                       updated_at               = NOW()
                 WHERE id = ? AND status = 'PENDING_DISBURSEMENT'
                """,
                mpesaRef, confirmedByUserId, loanId);
    }

    // ── Rich queries ──────────────────────────────────────────────────────────

    @Override
    public Optional<LoanDetail> findDetailById(UUID loanId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(LOAN_DETAIL_SQL + " WHERE la.id = ?",
                    this::mapLoanDetail, loanId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<LoanDetailWithSchedule> findDetailWithInstallments(UUID loanId) {
        return findDetailById(loanId).map(detail -> {
            List<InstallmentRow> schedule = jdbc.query(
                    """
                    SELECT id, installment_number, due_date,
                           principal_due, interest_due, total_due,
                           principal_paid, interest_paid, penalty_paid,
                           status, paid_at
                      FROM repayment_installments
                     WHERE loan_id = ?
                     ORDER BY installment_number
                    """,
                    (rs, row) -> new InstallmentRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getInt("installment_number"),
                            rs.getObject("due_date", LocalDate.class),
                            rs.getBigDecimal("principal_due"),
                            rs.getBigDecimal("interest_due"),
                            rs.getBigDecimal("total_due"),
                            rs.getBigDecimal("principal_paid"),
                            rs.getBigDecimal("interest_paid"),
                            rs.getBigDecimal("penalty_paid"),
                            rs.getString("status"),
                            rs.getTimestamp("paid_at") != null
                                    ? rs.getTimestamp("paid_at").toInstant() : null),
                    loanId);
            return new LoanDetailWithSchedule(detail, schedule);
        });
    }

    @Override
    public List<LoanSummaryRow> findLoanBook(UUID groupId, List<String> statuses) {
        String placeholders = statuses.stream().map(s -> "?").collect(Collectors.joining(","));
        Object[] params = new Object[statuses.size() + 1];
        params[0] = groupId;
        for (int i = 0; i < statuses.size(); i++) params[i + 1] = statuses.get(i);

        return jdbc.query(
                """
                SELECT la.id, la.loan_reference, m.id AS member_id, u.full_name, m.member_number,
                       lp.name AS product_name, la.status,
                       la.principal_amount,
                       la.principal_balance + la.accrued_interest + la.penalty_balance AS total_outstanding,
                       la.due_date,
                       (la.due_date < CURRENT_DATE AND la.status = 'ACTIVE') AS overdue
                  FROM loan_accounts la
                  JOIN members m ON m.id = la.member_id
                  JOIN users u ON u.id = m.user_id
                  JOIN loan_products lp ON lp.id = la.product_id
                 WHERE la.group_id = ?
                   AND la.status IN (%s)
                 ORDER BY la.due_date, m.member_number
                """.formatted(placeholders),
                (rs, row) -> new LoanSummaryRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("loan_reference"),
                        UUID.fromString(rs.getString("member_id")),
                        rs.getString("full_name"),
                        rs.getString("member_number"),
                        rs.getString("product_name"),
                        rs.getString("status"),
                        rs.getBigDecimal("principal_amount"),
                        rs.getBigDecimal("total_outstanding"),
                        rs.getObject("due_date", LocalDate.class),
                        rs.getBoolean("overdue")),
                params);
    }

    @Override
    public Optional<String> findMemberFullName(UUID memberId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT u.full_name FROM users u JOIN members m ON m.user_id=u.id WHERE m.id=?",
                    String.class, memberId));
        } catch (Exception e) { return Optional.empty(); }
    }

    // ── Aggregates ────────────────────────────────────────────────────────────

    @Override
    public BigDecimal totalActiveLoanBook(UUID groupId) {
        BigDecimal r = jdbc.queryForObject(
                "SELECT COALESCE(SUM(principal_balance+accrued_interest),0) FROM loan_accounts WHERE group_id=? AND status='ACTIVE'",
                BigDecimal.class, groupId);
        return r != null ? r : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal totalActiveLoansForMember(UUID memberId) {
        BigDecimal r = jdbc.queryForObject(
                "SELECT COALESCE(SUM(principal_balance+accrued_interest),0) FROM loan_accounts WHERE member_id=? AND status='ACTIVE'",
                BigDecimal.class, memberId);
        return r != null ? r : BigDecimal.ZERO;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String LOAN_DETAIL_SQL = """
            SELECT la.id, la.loan_reference,
                   m.id AS member_id, u.full_name AS member_name,
                   m.member_number, u.phone_number AS member_phone,
                   lp.id AS product_id, lp.name AS product_name, la.status,
                   la.principal_amount, la.total_interest_charged,
                   la.principal_balance, la.accrued_interest, la.penalty_balance,
                   la.total_principal_repaid, la.total_interest_repaid,
                   la.disbursement_date, la.due_date, la.disbursement_mpesa_ref
              FROM loan_accounts la
              JOIN members m ON m.id = la.member_id
              JOIN users u ON u.id = m.user_id
              JOIN loan_products lp ON lp.id = la.product_id
            """;

    private LoanDetail mapLoanDetail(ResultSet rs, int row) throws SQLException {
        return new LoanDetail(
                UUID.fromString(rs.getString("id")),
                rs.getString("loan_reference"),
                UUID.fromString(rs.getString("member_id")),
                rs.getString("member_name"),
                rs.getString("member_number"),
                rs.getString("member_phone"),
                UUID.fromString(rs.getString("product_id")),
                rs.getString("product_name"),
                rs.getString("status"),
                Money.ofKes(rs.getBigDecimal("principal_amount")),
                Money.ofKes(rs.getBigDecimal("total_interest_charged")),
                Money.ofKes(rs.getBigDecimal("principal_balance")),
                Money.ofKes(rs.getBigDecimal("accrued_interest")),
                Money.ofKes(rs.getBigDecimal("penalty_balance")),
                Money.ofKes(rs.getBigDecimal("total_principal_repaid")),
                Money.ofKes(rs.getBigDecimal("total_interest_repaid")),
                rs.getObject("disbursement_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getString("disbursement_mpesa_ref")
        );
    }

    @Override
    public List<PaymentRow> findRepaymentHistory(UUID loanId, UUID groupId) {
        return jdbc.query(
                """
                SELECT pr.amount, pr.payment_method, pr.mpesa_reference,
                       pr.narration, pr.recorded_at
                  FROM payment_records pr
                 WHERE pr.loan_id  = ?
                   AND pr.group_id = ?
                   AND pr.payment_type = 'LOAN_REPAYMENT'
                   AND pr.status = 'COMPLETED'
                 ORDER BY pr.recorded_at ASC
                """,
                (rs, row) -> new PaymentRow(
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_method"),
                        rs.getString("mpesa_reference"),
                        rs.getString("narration"),
                        rs.getTimestamp("recorded_at").toInstant()),
                loanId, groupId);
    }
}