package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.domain.model.ContributionCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for ContributionCycle persistence.
 *
 * Why JPA and not JDBC here?
 *   - findByGroupIdOrderByCycleNumberDesc and findByGroupIdAndFinancialYear
 *     are simple single-entity queries — Spring Data derives them for free from
 *     the method name or a straightforward JPQL @Query. No JdbcTemplate needed.
 *   - findOpenCycles uses typed enum params to avoid fragile string literals in
 *     JPQL against an @Enumerated(STRING) column.
 *
 * Cross-table joins (ledger, statements, scheduler ops) live in the JDBC adapters
 * in this same package — JPA and JDBC complement each other here.
 */
public interface ContributionCycleJpaRepository
        extends JpaRepository<ContributionCycleJpaEntity, UUID> {

    // Spring Data derives this entirely from the method name — no @Query needed
    List<ContributionCycleJpaEntity> findByGroupIdOrderByCycleNumberDesc(UUID groupId);

    @Query("""
           SELECT c FROM ContributionCycleJpaEntity c
            WHERE c.groupId = :groupId
              AND c.financialYear = :year
            ORDER BY c.cycleNumber
           """)
    List<ContributionCycleJpaEntity> findByGroupIdAndFinancialYear(
            @Param("groupId") UUID groupId,
            @Param("year") int year
    );

    /**
     * Returns OPEN and GRACE_PERIOD cycles for a group.
     * Uses typed CycleStatus params — not string literals — so a future enum rename
     * causes a compile error rather than silently returning empty results.
     */
    @Query("""
           SELECT c FROM ContributionCycleJpaEntity c
            WHERE c.groupId = :groupId
              AND c.status IN (:open, :grace)
            ORDER BY c.dueDate DESC
           """)
    List<ContributionCycleJpaEntity> findOpenCycles(
            @Param("groupId") UUID groupId,
            @Param("open")    ContributionCycle.CycleStatus open,
            @Param("grace")   ContributionCycle.CycleStatus grace
    );
}
