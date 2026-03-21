package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output port — cycle management operations that span multiple tables
 * (cycles + entries + members + groups). These are too complex for the
 * JPA entity repositories and require raw SQL.
 */
public interface ContributionCycleManagementRepository {

    List<CycleSummaryRow> findCyclesByGroup(UUID groupId);

    void setMgrBeneficiary(UUID cycleId, UUID groupId, UUID memberId, BigDecimal payoutAmount);

    String findMemberFullName(UUID memberId, UUID groupId);

    OpenCycleResult openCycle(UUID groupId, LocalDate dueDate, int graceDays, UUID mgrBeneficiaryId);

    record CycleSummaryRow(
            UUID id, int cycleNumber, int financialYear,
            LocalDate dueDate, LocalDate gracePeriodEnd, String status,
            BigDecimal totalExpectedAmount, BigDecimal totalCollectedAmount,
            UUID mgrBeneficiaryId, BigDecimal mgrPayoutAmount, java.time.Instant mgrPaidOutAt
    ) {}

    record OpenCycleResult(UUID cycleId, int cycleNumber, int year,
                            LocalDate dueDate, LocalDate gracePeriodEnd,
                            BigDecimal totalExpected, int memberCount) {}
}
