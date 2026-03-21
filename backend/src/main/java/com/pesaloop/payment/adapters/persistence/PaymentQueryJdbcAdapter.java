package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.PaymentQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — payment queries for history, unmatched C2B payments, paybill setup. */
@Repository
@RequiredArgsConstructor
public class PaymentQueryJdbcAdapter implements PaymentQueryRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void registerPaybillShortcode(UUID groupId, String shortcode, String type) {
        int updated = jdbc.update(
                "UPDATE groups SET mpesa_shortcode=?, mpesa_shortcode_type=?, updated_at=NOW() WHERE id=?",
                shortcode, type.toUpperCase(), groupId);
        if (updated == 0) throw new IllegalArgumentException("Group not found");
    }

    @Override
    public List<UnmatchedPaymentRow> findUnmatchedByGroup(UUID groupId) {
        return jdbc.query(
                """
                SELECT id, mpesa_transaction_id, amount, phone_number,
                       bill_ref_number, business_short_code, received_at
                  FROM unmatched_payments
                 WHERE business_short_code = (
                     SELECT mpesa_shortcode FROM groups WHERE id = ?
                 ) AND resolved = FALSE
                 ORDER BY received_at DESC
                """,
                this::mapUnmatched, groupId);
    }

    @Override
    public Optional<UnmatchedPaymentRow> findUnmatchedById(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id, mpesa_transaction_id, amount, phone_number, bill_ref_number, business_short_code, received_at FROM unmatched_payments WHERE id=? AND resolved=FALSE",
                    this::mapUnmatched, id));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void markUnmatchedResolved(UUID id, UUID resolvedByUserId, String notes) {
        jdbc.update(
                "UPDATE unmatched_payments SET resolved=TRUE, resolved_by=?, resolved_at=NOW(), resolution_note=? WHERE id=?",
                resolvedByUserId, notes, id);
    }

    @Override
    public List<PaymentHistoryRow> findPaymentHistory(UUID groupId, UUID memberId) {
        String memberFilter = memberId != null ? "AND pr.member_id = ?" : "";
        Object[] params = memberId != null ? new Object[]{groupId, memberId} : new Object[]{groupId};
        return jdbc.query(
                """
                SELECT pr.id, pr.member_id, m.member_number, u.full_name,
                       pr.payment_type, pr.amount, pr.payment_method,
                       pr.mpesa_reference, pr.narration, pr.status, pr.recorded_at
                  FROM payment_records pr
                  JOIN members m ON m.id = pr.member_id
                  JOIN users u ON u.id = m.user_id
                 WHERE pr.group_id = ? %s
                 ORDER BY pr.recorded_at DESC LIMIT 200
                """.formatted(memberFilter),
                (rs, row) -> new PaymentHistoryRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("member_id")),
                        rs.getString("member_number"), rs.getString("full_name"),
                        rs.getString("payment_type"), rs.getBigDecimal("amount"),
                        rs.getString("payment_method"), rs.getString("mpesa_reference"),
                        rs.getString("narration"), rs.getString("status"),
                        rs.getTimestamp("recorded_at").toInstant()),
                params);
    }

    private UnmatchedPaymentRow mapUnmatched(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new UnmatchedPaymentRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("mpesa_transaction_id"),
                rs.getBigDecimal("amount"),
                rs.getString("phone_number"),
                rs.getString("bill_ref_number"),
                rs.getString("business_short_code"),
                rs.getTimestamp("received_at").toInstant());
    }
}
