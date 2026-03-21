package com.pesaloop.contribution.adapters.scheduler;

import com.pesaloop.contribution.application.port.out.ContributionCycleSchedulerRepository;
import com.pesaloop.contribution.application.port.out.ContributionCycleSchedulerRepository.GroupCycleInfo;
import com.pesaloop.contribution.application.port.out.ContributionCycleSchedulerRepository.ReminderRow;
import com.pesaloop.notification.application.port.in.SendNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary adapter — triggers contribution cycle lifecycle operations on a schedule.
 *
 * Three jobs:
 *   1. CYCLE GENERATION (1st of month, 2 AM)
 *      Creates the next ContributionCycle for all active groups,
 *      generating one ContributionEntry per active member.
 *      Expected amount is snapshotted at creation — share price changes
 *      do not retroactively alter past cycles.
 *
 *   2. GRACE PERIOD TRANSITION (daily, 6:30 AM)
 *      OPEN → GRACE_PERIOD when due_date passes.
 *      GRACE_PERIOD → CLOSED when grace_period_end passes; unpaid entries
 *      move to member arrears.
 *      Also marks overdue loan installments and applies daily penalties.
 *
 *   3. CONTRIBUTION REMINDERS (daily, 8 AM)
 *      SMS reminders to members with unpaid entries due within 3 days,
 *      subject to group SMS wallet balance.
 *
 * No SQL in this class — all persistence delegated to
 * ContributionCycleSchedulerRepository (output port).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContributionCycleScheduler {

    private final ContributionCycleSchedulerRepository schedulerRepository;
    private final SendNotificationPort notificationPort;

    // ── 1. Monthly cycle generation ───────────────────────────────────────────

    @Scheduled(cron = "${pesaloop.scheduler.cycle-generation-cron:0 0 2 1 * *}")
    public void generateMonthlyContributionCycles() {
        LocalDate today = LocalDate.now();
        log.info("Generating contribution cycles for {}-{}", today.getYear(), today.getMonthValue());

        List<GroupCycleInfo> groups =
                schedulerRepository.findActiveGroupsForCycleGeneration(today.getYear());

        int created = 0;
        for (GroupCycleInfo group : groups) {
            try {
                // Only auto-generate MONTHLY cycles; other frequencies are admin-managed
                if (!"MONTHLY".equals(group.frequency())) continue;

                int nextCycle  = group.lastCycleNumber() + 1;
                int year       = today.getYear();
                // Use last day of month as due date (safe for all months)
                LocalDate due  = today.withDayOfMonth(today.lengthOfMonth());
                LocalDate grace = due.plusDays(group.gracePeriodDays());

                UUID cycleId = schedulerRepository.createCycleWithEntries(
                        group.groupId(), nextCycle, year, due, grace, group.currencyCode());

                log.info("Cycle created: group={} cycle={}/{} dueDate={}",
                        group.groupId(), nextCycle, year, due);
                created++;
            } catch (Exception e) {
                log.error("Failed to create cycle for group={}: {}",
                        group.groupId(), e.getMessage());
            }
        }
        log.info("Cycle generation complete: {} cycle(s) created", created);
    }

    // ── 2. Daily transitions + penalties ─────────────────────────────────────

    @Scheduled(cron = "${pesaloop.scheduler.overdue-check-cron:0 30 6 * * *}")
    @Transactional
    public void processCycleTransitions() {
        LocalDate today = LocalDate.now();

        // OPEN → GRACE_PERIOD
        int toGrace = schedulerRepository.transitionOpenToGrace(today);

        // GRACE_PERIOD → CLOSED (with arrears)
        List<UUID> toClose = schedulerRepository.findCyclesReadyToClose(today);
        for (UUID cycleId : toClose) {
            schedulerRepository.closeCycleAndMoveArrears(cycleId, today);
        }

        // Loan installments overdue
        int overdueCount = schedulerRepository.markOverdueInstallments(today);

        // Daily loan penalties
        schedulerRepository.applyLoanPenalties(today);

        if (toGrace > 0 || !toClose.isEmpty() || overdueCount > 0) {
            log.info("Cycle transitions: {} → GRACE_PERIOD, {} → CLOSED, {} installments OVERDUE",
                    toGrace, toClose.size(), overdueCount);
        }
    }

    // ── 3. Contribution reminders ─────────────────────────────────────────────

    @Scheduled(cron = "${pesaloop.scheduler.reminder-sms-cron:0 0 8 * * *}")
    public void sendContributionReminders() {
        LocalDate today = LocalDate.now();
        // Remind members whose cycle due date is within 3 days
        List<ReminderRow> reminders =
                schedulerRepository.findMembersNeedingReminders(today.plusDays(3));

        log.info("Sending {} contribution reminder(s)", reminders.size());

        for (ReminderRow r : reminders) {
            try {
                notificationPort.sendContributionReminder(
                        r.phone(),
                        "Member",          // memberNumber resolved by notification layer
                        r.groupName(),
                        r.expectedAmount().longValue(),
                        r.dueDate().toString(),
                        r.groupId(),
                        r.memberId());
            } catch (Exception e) {
                log.warn("Reminder failed: member={} error={}", r.memberId(), e.getMessage());
            }
        }
    }
}
