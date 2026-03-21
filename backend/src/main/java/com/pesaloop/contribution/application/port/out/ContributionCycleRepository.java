package com.pesaloop.contribution.application.port.out;

import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionCycleRepository {
    ContributionCycle save(ContributionCycle cycle);
    Optional<ContributionCycle> findById(UUID id);
    Optional<ContributionCycle> findCurrentOpenCycle(UUID groupId);
    List<ContributionCycle> findByGroupId(UUID groupId);
    List<ContributionCycle> findByGroupIdAndYear(UUID groupId, int year);
}
