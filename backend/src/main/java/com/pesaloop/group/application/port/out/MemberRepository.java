package com.pesaloop.group.application.port.out;

import com.pesaloop.group.domain.model.Member;
import com.pesaloop.group.domain.model.MemberStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Member save(Member member);
    Optional<Member> findById(UUID memberId);
    Optional<Member> findByGroupIdAndUserId(UUID groupId, UUID userId);
    Optional<Member> findByGroupIdAndMemberNumber(UUID groupId, String memberNumber);
    List<Member> findActiveByGroupId(UUID groupId);
    List<Member> findByGroupId(UUID groupId);
    int countActiveByGroupId(UUID groupId);

    /** Total shares across all active members — needed for dividend calculation */
    int sumSharesByGroupId(UUID groupId);

    /** Looks up the full name of a member by joining members → users. */
    java.util.Optional<String> findFullNameById(java.util.UUID memberId);

}
