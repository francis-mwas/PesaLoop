package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.application.port.out.ContributionCycleRepository;
import com.pesaloop.contribution.domain.model.ContributionCycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secondary adapter — implements ContributionCycleRepository using Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class ContributionCycleRepositoryAdapter implements ContributionCycleRepository {

    private final ContributionCycleJpaRepository jpa;
    private final ContributionMapper mapper;

    @Override
    public ContributionCycle save(ContributionCycle cycle) {
        if (cycle.getId() != null) {
            return jpa.findById(cycle.getId()).map(existing -> {
                existing.setTotalCollectedAmount(cycle.getTotalCollected().getAmount());
                existing.setStatus(cycle.getStatus());
                existing.setMgrBeneficiaryId(cycle.getMgrBeneficiaryMemberId());
                return mapper.toDomain(jpa.save(existing));
            }).orElseGet(() -> {
                ContributionCycleJpaEntity entity = mapper.toEntity(cycle);
                entity.setGroupId(cycle.getGroupId());
                return mapper.toDomain(jpa.save(entity));
            });
        }
        ContributionCycleJpaEntity entity = mapper.toEntity(cycle);
        entity.setGroupId(cycle.getGroupId());
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<ContributionCycle> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ContributionCycle> findCurrentOpenCycle(UUID groupId) {
        return jpa.findOpenCycles(groupId,
                        ContributionCycle.CycleStatus.OPEN,
                        ContributionCycle.CycleStatus.GRACE_PERIOD)
                .stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public List<ContributionCycle> findByGroupId(UUID groupId) {
        return jpa.findByGroupIdOrderByCycleNumberDesc(groupId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<ContributionCycle> findByGroupIdAndYear(UUID groupId, int year) {
        return jpa.findByGroupIdAndFinancialYear(groupId, year)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}