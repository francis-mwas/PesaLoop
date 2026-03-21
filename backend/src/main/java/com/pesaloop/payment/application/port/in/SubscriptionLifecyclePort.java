package com.pesaloop.payment.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

/** Input port — subscription lifecycle transitions. */
public interface SubscriptionLifecyclePort {
    void runDailyLifecycleTick();
    void activateSubscription(UUID groupId, String planCode, String mpesaRef, BigDecimal amountPaid);
    void cancelSubscription(UUID groupId, UUID cancelledByUserId, String reason);
}
