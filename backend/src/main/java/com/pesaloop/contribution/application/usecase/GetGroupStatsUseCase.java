package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.GetGroupStatsPort;
import com.pesaloop.contribution.application.port.out.GroupStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Returns YTD contribution stats and accrued interest for the group banner.
 * Delegates all queries to GroupStatsRepository — no SQL here.
 */
@Service
@RequiredArgsConstructor
public class GetGroupStatsUseCase implements GetGroupStatsPort {

    private final GroupStatsRepository statsRepository;

    @Override
    @Transactional(readOnly = true)
    public GroupStatsResult execute(UUID groupId, int year) {
        java.math.BigDecimal totalAccruedInterest =
                statsRepository.findTotalAccruedInterest(groupId);

        List<GroupStatsRepository.MemberYtdRow> rows =
                statsRepository.findMemberYtdContributions(groupId, year);

        java.math.BigDecimal totalYtdPaid = rows.stream()
                .map(GroupStatsRepository.MemberYtdRow::ytdPaid)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        List<MemberYtd> members = rows.stream()
                .map(r -> new MemberYtd(
                        r.memberId(), r.memberNumber(), r.fullName(),
                        r.ytdPaid(), r.ytdExpected()))
                .toList();

        return new GroupStatsResult(year, totalAccruedInterest, totalYtdPaid, members);
    }
}