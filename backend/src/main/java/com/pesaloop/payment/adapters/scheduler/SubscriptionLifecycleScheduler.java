package com.pesaloop.payment.adapters.scheduler;

import com.pesaloop.payment.application.port.in.SubscriptionLifecyclePort;
import com.pesaloop.payment.application.usecase.SubscriptionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Primary adapter — drives the subscription lifecycle on a daily schedule.
 *
 * This is the ONLY class that has @Scheduled for subscription lifecycle.
 * SubscriptionLifecycleService is a pure application service with no @Scheduled.
 * Separating the trigger from the logic means the service is easily testable
 * and can also be triggered manually by an admin endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionLifecycleScheduler {

    private final SubscriptionLifecyclePort lifecycleService;

    /**
     * Run daily at 02:00 Nairobi time.
     * Evaluates every group in TRIAL, GRACE, or SUSPENDED and transitions as needed.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Nairobi")
    public void dailyTick() {
        log.info("SubscriptionLifecycleScheduler: daily tick starting");
        lifecycleService.runDailyLifecycleTick();
    }
}
