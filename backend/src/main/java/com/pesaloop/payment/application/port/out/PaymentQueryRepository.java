package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port — payment history, unmatched payments, paybill registration. */
public interface PaymentQueryRepository {

    void registerPaybillShortcode(UUID groupId, String shortcode, String type);

    List<UnmatchedPaymentRow> findUnmatchedByGroup(UUID groupId);

    Optional<UnmatchedPaymentRow> findUnmatchedById(UUID id);

    void markUnmatchedResolved(UUID id, UUID resolvedByUserId, String notes);

    List<PaymentHistoryRow> findPaymentHistory(UUID groupId, UUID memberId);

    record UnmatchedPaymentRow(
            UUID id, String mpesaTransactionId, BigDecimal amount,
            String phoneNumber, String billRefNumber,
            String businessShortCode, Instant receivedAt
    ) {}

    record PaymentHistoryRow(
            UUID id, UUID memberId, String memberNumber, String fullName,
            String paymentType, BigDecimal amount, String paymentMethod,
            String mpesaReference, String narration, String status, Instant recordedAt
    ) {}
}
