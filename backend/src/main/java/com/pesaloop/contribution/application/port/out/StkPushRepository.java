package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — STK push request persistence.
 * InitiateStkPushUseCase depends on this, not on JdbcTemplate.
 */
public interface StkPushRepository {

    /** Persist a new PENDING STK push request before calling Daraja. */
    void create(UUID id, UUID groupId, UUID memberId, UUID entryId,
                String phone, BigDecimal amount);

    /** Update after Daraja responds with its IDs. */
    void updateWithMpesaIds(UUID id, String merchantRequestId, String checkoutRequestId);

    /** Mark as FAILED (Daraja call failed or was rejected). */
    void markFailed(UUID id, String reason);

    /** Check for a recent pending request — rate-limiting duplicate check. */
    Optional<String> findRecentPendingCheckoutId(UUID memberId, UUID entryId);

    /**
     * Finds a pending STK checkout ID for this member in this cycle.
     * Used to prevent duplicate pushes before the first one expires.
     */
    Optional<String> findPendingCheckoutIdByCycle(UUID memberId, UUID cycleId);

}
