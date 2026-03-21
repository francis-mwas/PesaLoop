package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — C2B payment processing operations.
 * C2bPaymentUseCase depends on this, not on JdbcTemplate.
 */
public interface C2bPaymentRepository {

    boolean alreadyProcessed(String mpesaTransactionId);

    Optional<UUID> findOpenEntryId(UUID groupId, UUID memberId);

    void applyC2bPayment(UUID groupId, UUID memberId, UUID entryId,
                          BigDecimal amount, String mpesaTransId,
                          String phone, String methodName);

    void saveUnmatched(String transId, BigDecimal amount, String phone,
                        String billRef, String shortCode);
}
