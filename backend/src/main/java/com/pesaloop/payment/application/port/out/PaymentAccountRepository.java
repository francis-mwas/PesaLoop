package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port — group payment account CRUD and subscription queries. */
public interface PaymentAccountRepository {

    List<PaymentAccountRow> findByGroupId(UUID groupId);

    UUID createAccount(UUID groupId, UUID createdByUserId, String accountType,
                       String provider, String accountNumber, String accountName,
                       String bankBranch, String bankSwiftCode, String displayLabel,
                       boolean isCollection, boolean isDisbursement);

    void deactivateAccount(UUID accountId, UUID groupId);

    void setPrimary(UUID accountId, UUID groupId, boolean isCollection, boolean isDisbursement);

    Optional<String> findShortcodeById(UUID accountId);

    void markC2bRegistered(UUID accountId);

    SubscriptionRow findSubscription(UUID groupId);

    record PaymentAccountRow(
            UUID id, String accountType, String provider,
            String accountNumber, String accountName,
            String bankBranch, String bankSwiftCode,
            boolean c2bRegistered, Instant c2bRegisteredAt,
            boolean isCollection, boolean isDisbursement, boolean isPrimary,
            String status, String displayLabel, Instant createdAt
    ) {}

    record SubscriptionRow(
            String status, String planCode, String planName,
            BigDecimal monthlyFeeKes, Instant currentPeriodEnd,
            Instant trialEndsAt, String pesaLoopPaybill, String groupSlug
    ) {}
}
