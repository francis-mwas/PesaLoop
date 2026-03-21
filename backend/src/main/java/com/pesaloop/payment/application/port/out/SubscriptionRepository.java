package com.pesaloop.payment.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — subscription lifecycle persistence.
 * Declared in domain. Implemented in adapters/persistence.
 */
public interface SubscriptionRepository {

    /** All groups currently in TRIAL, GRACE, or SUSPENDED needing daily evaluation. */
    List<GroupLifecycleRow> findGroupsNeedingAction();

    /** Current subscription status for a group. */
    String findStatus(UUID groupId);

    /** Transition TRIAL → GRACE. Sets grace_period_end. */
    void startGrace(UUID groupId, int graceDays);

    /** Transition GRACE → SUSPENDED. */
    void suspend(UUID groupId);

    /** Transition SUSPENDED → DORMANT. */
    void markDormant(UUID groupId);

    /** Activate (TRIAL/GRACE/SUSPENDED/DORMANT → ACTIVE). Records payment. */
    void activate(UUID groupId, String planCode,
                  LocalDate periodStart, LocalDate periodEnd,
                  Instant nextInvoiceAt, String mpesaRef);

    /** Cancel. */
    void cancel(UUID groupId, String reason, UUID cancelledByUserId);

    /** Record that a reminder SMS was sent. */
    void recordReminderSent(UUID groupId);

    /** Generate an invoice for a group. */
    void generateInvoice(UUID groupId, String planCode, BigDecimal feeKes, String groupSlug);

    /** Mark the most recent open invoice as paid. */
    void markInvoicePaid(UUID groupId, String mpesaRef);

    /** Get monthly fee for a plan. */
    BigDecimal monthlyFeeForPlan(String planCode);

    /** Get group slug. */
    String findGroupSlug(UUID groupId);

    /** Get config value as int (e.g. trial_days, grace_days). */
    int getConfig(String key, int defaultValue);

    /** Log a subscription lifecycle event. */
    void logEvent(UUID groupId, String eventType, String fromStatus, String toStatus,
                  String triggeredBy, UUID actorId, String notes);

    record GroupLifecycleRow(
            UUID groupId,
            String groupName,
            String groupSlug,
            String planCode,
            String status,
            Integer daysRemaining,
            String requiredAction,
            String adminName,
            String adminPhone,
            int reminderCount
    ) {}

    /** Find a group's UUID by its slug. */
    java.util.Optional<UUID> findGroupIdBySlug(String slug);

    /** Find a group's UUID by partial name match. */
    java.util.Optional<UUID> findGroupIdByName(String namePart);

}
