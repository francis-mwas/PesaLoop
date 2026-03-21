package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.GuarantorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GuarantorJdbcAdapter implements GuarantorRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void create(UUID loanId, UUID groupId, UUID guarantorMemberId) {
        jdbc.update(
                """
                INSERT INTO loan_guarantors
                    (id, loan_id, group_id, guarantor_member_id, status, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 'PENDING', NOW())
                ON CONFLICT (loan_id, guarantor_member_id) DO NOTHING
                """,
                loanId, groupId, guarantorMemberId);
    }

    @Override
    public int respond(UUID loanId, UUID guarantorMemberId, boolean accepted, String note) {
        return jdbc.update(
                """
                UPDATE loan_guarantors
                   SET status        = ?,
                       responded_at  = NOW(),
                       response_note = ?
                 WHERE loan_id = ? AND guarantor_member_id = ? AND status = 'PENDING'
                """,
                accepted ? "ACCEPTED" : "DECLINED",
                note, loanId, guarantorMemberId);
    }

    @Override
    public int countAccepted(UUID loanId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loan_guarantors WHERE loan_id=? AND status='ACCEPTED'",
                Integer.class, loanId);
        return count != null ? count : 0;
    }

    @Override
    public List<GuarantorRecord> findByLoanId(UUID loanId, UUID groupId) {
        return jdbc.query(
                """
                SELECT lg.id, lg.guarantor_member_id, u.full_name,
                       m.member_number, m.phone_number,
                       lg.status, lg.responded_at, lg.response_note
                  FROM loan_guarantors lg
                  JOIN members m ON m.id = lg.guarantor_member_id
                  JOIN users u ON u.id = m.user_id
                 WHERE lg.loan_id = ? AND lg.group_id = ?
                 ORDER BY lg.created_at
                """,
                (rs, row) -> new GuarantorRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("guarantor_member_id")),
                        rs.getString("full_name"),
                        rs.getString("member_number"),
                        rs.getString("phone_number"),
                        rs.getString("status"),
                        rs.getObject("responded_at", java.time.Instant.class),
                        rs.getString("response_note")
                ),
                loanId, groupId);
    }

    @Override
    public Optional<String> findLoanStatus(UUID loanId, UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT status FROM loan_accounts WHERE id=? AND group_id=?",
                    String.class, loanId, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<UUID> findApplicantMemberId(UUID loanId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT member_id FROM loan_accounts WHERE id=?",
                    UUID.class, loanId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findLoanReference(UUID loanId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT loan_reference FROM loan_accounts WHERE id=?",
                    String.class, loanId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public int findRequiredGuarantorCount(UUID loanId) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT lp.max_guarantors FROM loan_products lp JOIN loan_accounts la ON la.product_id=lp.id WHERE la.id=?",
                    Integer.class, loanId);
            return count != null ? count : 1;
        } catch (Exception e) { return 1; }
    }

    @Override
    public Optional<String> findMemberPhone(UUID memberId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT phone_number FROM members WHERE id=?",
                    String.class, memberId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findMemberFullName(UUID memberId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT u.full_name FROM users u JOIN members m ON m.user_id=u.id WHERE m.id=?",
                    String.class, memberId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findGroupName(UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT name FROM groups WHERE id=?", String.class, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findAdminPhone(UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT u.phone_number FROM users u JOIN members m ON m.user_id=u.id
                     WHERE m.group_id=? AND m.role='ADMIN' AND m.status='ACTIVE'
                     LIMIT 1
                    """,
                    String.class, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void advanceToApproval(UUID loanId) {
        jdbc.update(
                "UPDATE loan_accounts SET status='PENDING_APPROVAL', updated_at=NOW() WHERE id=?",
                loanId);
    }
}
