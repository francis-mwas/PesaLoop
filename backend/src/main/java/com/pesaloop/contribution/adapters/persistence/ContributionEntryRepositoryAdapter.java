package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.ContributionEntryRepository;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secondary adapter — implements ContributionEntryRepository using Spring Data JPA + JDBC. */
@Repository
@RequiredArgsConstructor
public class ContributionEntryRepositoryAdapter implements ContributionEntryRepository {

    private final ContributionEntryJpaRepository jpa;
    private final ContributionMapper mapper;
    private final JdbcTemplate jdbc;

    @Override
    public ContributionEntry save(ContributionEntry entry) {
        ContributionEntryJpaEntity entity = mapper.toEntity(entry);
        entity.setGroupId(entry.getGroupId());
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<ContributionEntry> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ContributionEntry> findByCycleIdAndMemberId(UUID cycleId, UUID memberId) {
        return jpa.findByCycleIdAndMemberId(cycleId, memberId).map(mapper::toDomain);
    }

    @Override
    public List<ContributionEntry> findByCycleId(UUID cycleId) {
        return jpa.findByCycleIdOrderByMemberId(cycleId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ContributionEntry> findByMemberId(UUID memberId) {
        return jpa.findByMemberIdOrderByCreatedAtDesc(memberId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ContributionEntry> findByMemberIdAndYear(UUID memberId, int year) {
        return jpa.findByMemberIdAndYear(memberId, year)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ContributionEntry> findUnpaidByCycleId(UUID cycleId) {
        return jpa.findUnpaidByCycleId(cycleId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<EntryPayment> findPaymentsByEntryMember(UUID cycleId, UUID memberId, UUID groupId) {
        return jdbc.query(
                """
                SELECT pr.amount, pr.payment_method, pr.mpesa_reference,
                       pr.narration, pr.recorded_at
                  FROM payment_records pr
                  JOIN contribution_entries ce ON ce.id = pr.entry_id
                 WHERE ce.cycle_id  = ?
                   AND ce.member_id = ?
                   AND pr.group_id  = ?
                   AND pr.payment_type = 'CONTRIBUTION'
                   AND pr.status = 'COMPLETED'
                 ORDER BY pr.recorded_at ASC
                """,
                (rs, row) -> new EntryPayment(
                        rs.getBigDecimal("amount"),
                        rs.getString("payment_method"),
                        rs.getString("mpesa_reference"),
                        rs.getString("narration"),
                        rs.getTimestamp("recorded_at").toInstant()),
                cycleId, memberId, groupId);
    }
}