package com.pesaloop.group.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — rich member queries that join members with users and groups.
 * These go beyond what the JPA entity alone can provide.
 */
public interface MemberQueryRepository {

    List<MemberSummary> findMemberSummariesByGroupId(UUID groupId, String statusFilter);

    Optional<MemberSummary> findMemberSummaryById(UUID memberId, UUID groupId);

    Optional<UUID> findMemberIdByUserId(UUID userId, UUID groupId);

    int[] findShareLimits(UUID groupId);

    void updateShares(UUID memberId, UUID groupId, int newShares, UUID approvedByUserId, String reason);

    record MemberSummary(
            UUID memberId, String memberNumber, String fullName, String phoneNumber,
            String role, String status, int sharesOwned,
            BigDecimal savingsBalance, BigDecimal arrearsBalance, BigDecimal finesBalance,
            LocalDate joinedOn, BigDecimal sharePriceAmount, int activeLoans
    ) {}
}
