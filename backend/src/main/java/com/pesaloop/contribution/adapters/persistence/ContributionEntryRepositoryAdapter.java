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
        if (entry.getId() != null) {
            // For existing entities: load the JPA instance already in the session,
            // update its fields in-place, then save. This prevents the
            // "different object with same identifier" Hibernate conflict that occurs
            // when mapper.toEntity() creates a second Java object for the same DB row.
            return jpa.findById(entry.getId()).map(existing -> {
                existing.setPaidAmount(entry.getPaidAmount().getAmount());
                existing.setStatus(entry.getStatus());
                existing.setLastPaymentMethod(entry.getLastPaymentMethod());
                existing.setLastMpesaReference(entry.getLastMpesaReference());
                existing.setRecordedBy(entry.getRecordedBy());
                existing.setFirstPaymentAt(entry.getFirstPaymentAt());
                existing.setFullyPaidAt(entry.getFullyPaidAt());
                existing.setArrearsCarriedForward(
                        entry.getArrearsCarriedForward() != null
                                ? entry.getArrearsCarriedForward().getAmount() : null);
                return mapper.toDomain(jpa.save(existing));
            }).orElseGet(() -> {
                // Entity not yet in session — safe to create fresh
                ContributionEntryJpaEntity entity = mapper.toEntity(entry);
                entity.setGroupId(entry.getGroupId());
                return mapper.toDomain(jpa.save(entity));
            });
        }
        // Brand new entry — no id yet
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

    private static final java.util.Set<String> REFERENCE_CHANNELS = java.util.Set.of(
            "MPESA_PAYBILL", "MPESA_TILL", "MPESA_STK_PUSH", "BANK_TRANSFER"
    );

    @Override
    public boolean isDuplicateReference(UUID groupId, String reference, String paymentMethod) {
        if (reference == null || reference.isBlank()) return false;
        if (!REFERENCE_CHANNELS.contains(paymentMethod)) return false;
        Integer count = jdbc.queryForObject(
                """  
                      SELECT COUNT(*) FROM payment_records
                         WHERE group_id        = ?
                           AND mpesa_reference  = ?
                           AND payment_method   = ?
                           AND status          != 'REVERSED'              
                   """,
                Integer.class, groupId, reference, paymentMethod);
        return count != null && count > 0;
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