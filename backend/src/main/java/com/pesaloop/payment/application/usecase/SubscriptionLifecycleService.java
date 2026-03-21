package com.pesaloop.payment.application.usecase;

import com.pesaloop.payment.application.port.in.SubscriptionLifecyclePort;

import com.pesaloop.notification.application.usecase.SmsService;
import com.pesaloop.payment.application.port.out.SubscriptionRepository;
import com.pesaloop.payment.application.port.out.SubscriptionRepository.GroupLifecycleRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Subscription lifecycle state machine.
 *
 * All persistence delegated to SubscriptionRepository port.
 * No JdbcTemplate or raw SQL here.
 *
 * States: TRIAL → GRACE → SUSPENDED → DORMANT → (ACTIVE on payment)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService implements SubscriptionLifecyclePort {

    private final SubscriptionRepository subscriptions;
    private final SmsService smsService;

    // ── Daily scheduler ───────────────────────────────────────────────────────

    public void runDailyLifecycleTick() {
        log.info("Subscription lifecycle tick starting");
        int transitioned = 0, reminded = 0;

        for (GroupLifecycleRow group : subscriptions.findGroupsNeedingAction()) {
            try {
                switch (group.requiredAction()) {
                    case "START_GRACE"  -> { startGrace(group);   transitioned++; }
                    case "SUSPEND"      -> { suspend(group);       transitioned++; }
                    case "MARK_DORMANT" -> { markDormant(group);   transitioned++; }
                    case "SEND_REMINDER"-> { sendReminder(group);  reminded++; }
                }
            } catch (Exception e) {
                log.error("Lifecycle tick failed for group={}: {}", group.groupId(), e.getMessage());
            }
        }
        log.info("Lifecycle tick complete: {} transitions, {} reminders", transitioned, reminded);
    }

    // ── Payment-triggered activation (immediate) ──────────────────────────────

    @Transactional
    public void activateSubscription(UUID groupId, String planCode,
                                      String mpesaRef, BigDecimal amountPaid) {
        String currentStatus = subscriptions.findStatus(groupId);

        if ("CANCELLED".equals(currentStatus)) {
            log.warn("Payment for CANCELLED group={} — ignoring", groupId);
            return;
        }

        LocalDate periodStart = LocalDate.now();
        LocalDate periodEnd   = periodStart.plusMonths(1);
        String resolvedPlan   = planCode != null ? planCode : detectPlan(amountPaid);

        subscriptions.activate(groupId, resolvedPlan,
                periodStart, periodEnd,
                periodEnd.atStartOfDay()
                         .atZone(java.time.ZoneId.of("Africa/Nairobi"))
                         .toInstant(),
                mpesaRef);

        subscriptions.logEvent(groupId, "REACTIVATED", currentStatus, "ACTIVE",
                "PAYMENT", null, "M-Pesa ref: " + mpesaRef);

        log.info("Subscription activated: group={} plan={} mpesaRef={}", groupId, resolvedPlan, mpesaRef);
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    @Transactional
    public void startGrace(GroupLifecycleRow group) {
        int graceDays = subscriptions.getConfig("grace_days", 7);
        subscriptions.startGrace(group.groupId(), graceDays);

        BigDecimal fee = subscriptions.monthlyFeeForPlan(group.planCode());
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            String slug = subscriptions.findGroupSlug(group.groupId());
            subscriptions.generateInvoice(group.groupId(), group.planCode(), fee, slug);
        }

        smsService.sendSubscriptionSms(
                group.adminPhone(),
                buildGraceStartSms(group.groupName(), graceDays),
                group.groupId());

        subscriptions.logEvent(group.groupId(), "GRACE_STARTED", "TRIAL", "GRACE", "SYSTEM", null, null);
        log.info("Grace started: group={} graceDays={}", group.groupId(), graceDays);
    }

    @Transactional
    public void suspend(GroupLifecycleRow group) {
        subscriptions.suspend(group.groupId());
        smsService.sendSubscriptionSms(
                group.adminPhone(), buildSuspensionSms(group.groupName()), group.groupId());
        subscriptions.logEvent(group.groupId(), "SUSPENDED", "GRACE", "SUSPENDED", "SYSTEM", null, null);
        log.info("Group suspended: group={}", group.groupId());
    }

    @Transactional
    public void markDormant(GroupLifecycleRow group) {
        subscriptions.markDormant(group.groupId());
        subscriptions.logEvent(group.groupId(), "DORMANT", "SUSPENDED", "DORMANT", "SYSTEM", null, null);
    }

    @Transactional
    public void sendReminder(GroupLifecycleRow group) {
        int daysLeft = group.daysRemaining() != null ? group.daysRemaining() : 0;
        smsService.sendSubscriptionSms(
                group.adminPhone(),
                buildReminderSms(group.groupName(), group.status(), daysLeft, group.planCode()),
                group.groupId());
        subscriptions.recordReminderSent(group.groupId());
        subscriptions.logEvent(group.groupId(), "GRACE_REMINDER_SENT",
                group.status(), group.status(), "SYSTEM", null, "Days remaining: " + daysLeft);
    }

    @Transactional
    public void cancelSubscription(UUID groupId, UUID cancelledByUserId, String reason) {
        String current = subscriptions.findStatus(groupId);
        subscriptions.cancel(groupId, reason, cancelledByUserId);
        subscriptions.logEvent(groupId, "CANCELLED", current, "CANCELLED", "ADMIN", cancelledByUserId, reason);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String detectPlan(BigDecimal amount) {
        if (amount == null) return "GROWTH";
        int kes = amount.intValue();
        if (kes >= 15000) return "PRO";
        if (kes >= 5000)  return "GROWTH";
        if (kes >= 1500)  return "PRO";
        if (kes >= 500)   return "GROWTH";
        return "FREE";
    }

    private String buildGraceStartSms(String group, int days) {
        return String.format("PesaLoop: Your free trial for %s has ended. You have %d days to pay. " +
                "Pay KES 500 via M-Pesa Paybill 123456, Account: pesaloop.", group, days);
    }

    private String buildReminderSms(String group, String status, int daysLeft, String plan) {
        int price = "PRO".equals(plan) ? 1500 : 500;
        if ("TRIAL".equals(status))
            return String.format("PesaLoop: %d days left on your free trial for %s. Pay KES %d to continue.", daysLeft, group, price);
        return String.format("URGENT - PesaLoop: %s pauses in %d day%s. Pay KES %d via Paybill 123456. Data is safe.", group, daysLeft, daysLeft==1?"":"s", price);
    }

    private String buildSuspensionSms(String group) {
        return String.format("PesaLoop: %s has been paused. Pay KES 500 via M-Pesa Paybill 123456, Account: pesaloop to reactivate instantly.", group);
    }
}
