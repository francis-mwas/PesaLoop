package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.StkPushRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StkPushJdbcAdapter implements StkPushRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void create(UUID id, UUID groupId, UUID memberId, UUID entryId,
                       String phone, BigDecimal amount) {
        jdbc.update(
                """
                INSERT INTO stk_push_requests
                    (id, group_id, member_id, entry_id, phone_number, amount,
                     purpose, status, initiated_at, expires_at)
                VALUES (?,?,?,?,?,?,'CONTRIBUTION','PENDING', NOW(), NOW() + INTERVAL '5 minutes')
                """,
                id, groupId, memberId, entryId, phone, amount);
    }

    @Override
    public void updateWithMpesaIds(UUID id, String merchantRequestId, String checkoutRequestId) {
        jdbc.update(
                "UPDATE stk_push_requests SET merchant_request_id=?, checkout_request_id=? WHERE id=?",
                merchantRequestId, checkoutRequestId, id);
    }

    @Override
    public void markFailed(UUID id, String reason) {
        jdbc.update(
                "UPDATE stk_push_requests SET status='FAILED', failure_reason=? WHERE id=?",
                reason, id);
    }

    @Override
    public Optional<String> findRecentPendingCheckoutId(UUID memberId, UUID entryId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT checkout_request_id
                      FROM stk_push_requests
                     WHERE member_id = ?
                       AND entry_id  = ?
                       AND status    = 'PENDING'
                       AND expires_at > NOW()
                     ORDER BY initiated_at DESC
                     LIMIT 1
                    """,
                    String.class, memberId, entryId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findPendingCheckoutIdByCycle(UUID memberId, UUID cycleId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    """
                    SELECT checkout_request_id
                      FROM stk_push_requests
                     WHERE member_id = ?
                       AND entry_id = (
                           SELECT id FROM contribution_entries
                            WHERE member_id = ? AND cycle_id = ?
                       )
                       AND status = 'PENDING'
                       AND expires_at > NOW()
                     ORDER BY initiated_at DESC
                     LIMIT 1
                    """,
                    String.class, memberId, memberId, cycleId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

