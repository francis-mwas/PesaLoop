package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.DisbursementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DisbursementJdbcAdapter implements DisbursementRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void create(UUID id, UUID groupId, String instructionType,
                       UUID loanId, String sourceReference,
                       UUID recipientMemberId, String recipientName, String recipientPhone,
                       String suggestedAccountReference, BigDecimal amountKes,
                       Instant expiresAt, UUID issuedByUserId) {
        jdbc.update(
                """
                INSERT INTO disbursement_instructions (
                    id, group_id, instruction_type, loan_id, source_reference,
                    recipient_member_id, recipient_name, recipient_phone,
                    suggested_account_reference, amount_kes, status,
                    expires_at, issued_by, issued_at, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,NOW(),NOW(),NOW())
                """,
                id, groupId, instructionType, loanId, sourceReference,
                recipientMemberId, recipientName, recipientPhone,
                suggestedAccountReference, amountKes,
                expiresAt, issuedByUserId);
    }

    @Override
    public Optional<DisbursementRecord> findPendingById(UUID id, UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT id, group_id, instruction_type, loan_id, source_reference,
                           recipient_member_id, recipient_name, recipient_phone,
                           suggested_account_reference, amount_kes, status,
                           expires_at, issued_at
                      FROM disbursement_instructions
                     WHERE id=? AND group_id=?
                    """,
                    (rs, row) -> mapRecord(rs),
                    id, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public List<DisbursementRecord> findPendingByGroup(UUID groupId) {
        return jdbc.query(
                """
                SELECT id, group_id, instruction_type, loan_id, source_reference,
                       recipient_member_id, recipient_name, recipient_phone,
                       suggested_account_reference, amount_kes, status,
                       expires_at, issued_at
                  FROM disbursement_instructions
                 WHERE group_id=? AND status='PENDING' AND expires_at>NOW()
                 ORDER BY issued_at
                """,
                (rs, row) -> mapRecord(rs),
                groupId);
    }

    @Override
    public int countPendingByGroup(UUID groupId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM disbursement_instructions WHERE group_id=? AND status='PENDING' AND expires_at>NOW()",
                Integer.class, groupId);
        return count != null ? count : 0;
    }

    @Override
    public void confirm(UUID id, UUID confirmedByUserId,
                        String externalMpesaRef, String notes) {
        jdbc.update(
                """
                UPDATE disbursement_instructions
                   SET status             = 'CONFIRMED',
                       confirmed_by       = ?,
                       confirmed_at       = NOW(),
                       external_mpesa_ref = ?,
                       confirmation_notes = ?,
                       updated_at         = NOW()
                 WHERE id = ?
                """,
                confirmedByUserId, externalMpesaRef, notes, id);
    }

    @Override
    public void cancel(UUID id, String reason, UUID cancelledByUserId) {
        jdbc.update(
                """
                UPDATE disbursement_instructions
                   SET status='CANCELLED', confirmation_notes=?,
                       confirmed_by=?, confirmed_at=NOW(), updated_at=NOW()
                 WHERE id=? AND status='PENDING'
                """,
                reason, cancelledByUserId, id);
        // Revert loan to APPROVED
        jdbc.update(
                """
                UPDATE loan_accounts SET status='APPROVED', updated_at=NOW()
                 WHERE id=(SELECT loan_id FROM disbursement_instructions WHERE id=?)
                   AND status='PENDING_DISBURSEMENT'
                """,
                id);
    }

    @Override
    public boolean mpesaRefAlreadyUsed(String externalMpesaRef) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM disbursement_instructions WHERE external_mpesa_ref=? AND status='CONFIRMED')",
                Boolean.class, externalMpesaRef);
        return Boolean.TRUE.equals(exists);
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
    public int getConfig(UUID groupId, String key, int defaultValue) {
        try {
            Integer val = jdbc.queryForObject(
                    "SELECT config_value::INT FROM platform_config WHERE config_key=? AND (group_id=? OR group_id IS NULL) ORDER BY group_id NULLS LAST LIMIT 1",
                    Integer.class, key, groupId);
            return val != null ? val : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }

    private DisbursementRecord mapRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DisbursementRecord(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("group_id")),
                rs.getString("instruction_type"),
                rs.getString("loan_id") != null ? UUID.fromString(rs.getString("loan_id")) : null,
                rs.getString("source_reference"),
                UUID.fromString(rs.getString("recipient_member_id")),
                rs.getString("recipient_name"),
                rs.getString("recipient_phone"),
                rs.getString("suggested_account_reference"),
                rs.getBigDecimal("amount_kes"),
                rs.getString("status"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("issued_at").toInstant()
        );
    }
}
