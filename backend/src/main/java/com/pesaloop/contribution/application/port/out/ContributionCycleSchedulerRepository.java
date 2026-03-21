package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output port — all persistence operations needed by the contribution cycle scheduler.
 * ContributionCycleScheduler depends on this, not on JdbcTemplate.
 *
 * Three responsibilities:
 *   1. Cycle generation  — find active groups, create cycles + entries
 *   2. Cycle transitions — OPEN→GRACE, GRACE→CLOSED, arrears, loan penalties
 *   3. Reminders         — find members with unpaid entries due soon
 */
public interface ContributionCycleSchedulerRepository {

    // ── 1. Cycle generation ───────────────────────────────────────────────────

    List<GroupCycleInfo> findActiveGroupsForCycleGeneration(int currentYear);

    UUID createCycleWithEntries(UUID groupId, int cycleNumber, int financialYear,
                                 LocalDate dueDate, LocalDate gracePeriodEnd,
                                 String currencyCode);

    // ── 2. Cycle transitions ──────────────────────────────────────────────────

    /** OPEN → GRACE_PERIOD for cycles whose due_date has passed. */
    int transitionOpenToGrace(LocalDate today);

    /** Returns cycle IDs in GRACE_PERIOD whose grace_period_end < today. */
    List<UUID> findCyclesReadyToClose(LocalDate today);

    /** Move unpaid entries to arrears, mark entries ARREARS_APPLIED, close cycle. */
    void closeCycleAndMoveArrears(UUID cycleId, LocalDate today);

    /** Mark installments OVERDUE where due_date < today and still PENDING. */
    int markOverdueInstallments(LocalDate today);

    /** Apply daily penalty to loans with overdue installments past their grace period. */
    void applyLoanPenalties(LocalDate today);

    // ── 3. Reminders ──────────────────────────────────────────────────────────

    /** Members with PENDING/PARTIAL entries in OPEN/GRACE cycles due within daysAhead. */
    List<ReminderRow> findMembersNeedingReminders(LocalDate dueBefore);

    // ── Records ───────────────────────────────────────────────────────────────

    record GroupCycleInfo(
            UUID groupId,
            String currencyCode,
            String frequency,
            Integer customFrequencyDays,
            int gracePeriodDays,
            int financialYearStartMonth,
            int lastCycleNumber,
            int lastFinancialYear
    ) {}

    record ReminderRow(
            String phone,
            UUID memberId,
            UUID groupId,
            String groupName,
            BigDecimal expectedAmount,
            LocalDate dueDate,
            int smsBalance
    ) {}
}
