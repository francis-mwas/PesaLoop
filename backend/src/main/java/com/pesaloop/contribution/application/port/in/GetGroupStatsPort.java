package com.pesaloop.contribution.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Input port — fetches YTD group stats for the contributions banner. */
public interface GetGroupStatsPort {
    GroupStatsResult execute(UUID groupId, int year);

    record GroupStatsResult(
            int year,
            BigDecimal totalAccruedInterest,
            BigDecimal totalYtdContributions,
            List<MemberYtd> members
    ) {}

    record MemberYtd(
            UUID memberId,
            String memberNumber,
            String fullName,
            BigDecimal ytdPaid,
            BigDecimal ytdExpected
    ) {}
}