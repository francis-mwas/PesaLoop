package com.pesaloop.group.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {

    Optional<MemberJpaEntity> findByGroupIdAndUserId(UUID groupId, UUID userId);

    Optional<MemberJpaEntity> findByGroupIdAndMemberNumber(UUID groupId, String memberNumber);

    @Query("SELECT m FROM MemberJpaEntity m WHERE m.groupId = :groupId AND m.status = 'ACTIVE' ORDER BY m.memberNumber")
    List<MemberJpaEntity> findActiveByGroupId(UUID groupId);

    List<MemberJpaEntity> findByGroupIdOrderByMemberNumber(UUID groupId);

    @Query("SELECT COUNT(m) FROM MemberJpaEntity m WHERE m.groupId = :groupId AND m.status = 'ACTIVE'")
    int countActiveByGroupId(UUID groupId);

    @Query("SELECT COALESCE(SUM(m.sharesOwned), 0) FROM MemberJpaEntity m WHERE m.groupId = :groupId AND m.status = 'ACTIVE'")
    int sumSharesByGroupId(UUID groupId);
}
