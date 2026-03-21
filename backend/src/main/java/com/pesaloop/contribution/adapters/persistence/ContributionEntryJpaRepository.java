package com.pesaloop.contribution.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionEntryJpaRepository
        extends JpaRepository<ContributionEntryJpaEntity, UUID> {

    Optional<ContributionEntryJpaEntity> findByCycleIdAndMemberId(UUID cycleId, UUID memberId);

    List<ContributionEntryJpaEntity> findByCycleIdOrderByMemberId(UUID cycleId);

    List<ContributionEntryJpaEntity> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    @Query(value = """
           SELECT e.* FROM contribution_entries e
            JOIN contribution_cycles c ON c.id = e.cycle_id
            WHERE e.member_id = :memberId
              AND c.financial_year = :year
            ORDER BY c.cycle_number
           """, nativeQuery = true)
    List<ContributionEntryJpaEntity> findByMemberIdAndYear(
            @org.springframework.data.repository.query.Param("memberId") UUID memberId,
            @org.springframework.data.repository.query.Param("year") int year);

    @Query("""
           SELECT e FROM ContributionEntryJpaEntity e
            WHERE e.cycleId = :cycleId
              AND e.status IN ('PENDING','PARTIAL')
           """)
    List<ContributionEntryJpaEntity> findUnpaidByCycleId(UUID cycleId);
}
