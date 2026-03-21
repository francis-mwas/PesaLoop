package com.pesaloop.shared.domain;

import java.time.LocalDate;

/**
 * Value type carrying the subscription state needed by the access guard.
 * Read once per guarded request from group_subscriptions JOIN groups.
 */
public record SubscriptionInfo(
        String status,
        String planCode,
        String groupSlug,
        LocalDate cancelledAt   // null when not cancelled
) {}
