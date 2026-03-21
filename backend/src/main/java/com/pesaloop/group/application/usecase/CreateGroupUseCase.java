package com.pesaloop.group.application.usecase;

import com.pesaloop.group.application.port.in.CreateGroupPort;

import com.pesaloop.group.application.dto.CreateGroupRequest;
import com.pesaloop.group.application.dto.GroupResponse;
import com.pesaloop.group.domain.model.*;
import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.out.GroupSetupRepository;
import com.pesaloop.group.application.port.out.SlugGenerator;
import com.pesaloop.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateGroupUseCase implements CreateGroupPort {

    private final GroupRepository groupRepository;
    private final SlugGenerator slugGenerator;
    private final GroupSetupRepository groupSetup;

    @Transactional
    public GroupResponse execute(CreateGroupRequest request, UUID createdByUserId) {

        String slug = request.slug() != null
                ? request.slug().toLowerCase().replaceAll("[^a-z0-9-]", "-")
                : slugGenerator.generate(request.name());

        if (groupRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Group slug '%s' is already taken".formatted(slug));
        }

        ShareConfig shareConfig;
        if (request.sharesMode()) {
            shareConfig = ShareConfig.builder()
                    .pricePerShare(Money.of(BigDecimal.valueOf(request.sharePriceAmount()), request.currencyCode()))
                    .minimumShares(request.minimumShares())
                    .maximumShares(request.maximumShares())
                    .sharesMode(true)
                    .allowShareChangeMidYear(request.allowShareChangeMidYear())
                    .build();
        } else {
            shareConfig = ShareConfig.flatAmount(
                    Money.of(BigDecimal.valueOf(request.fixedContributionAmount()), request.currencyCode()));
        }

        Group group = Group.create(
                request.name(), slug,
                Set.copyOf(request.groupTypes()),
                request.currencyCode(), shareConfig,
                request.contributionFrequency(), createdByUserId);

        Group saved = groupRepository.save(group);

        // ── Create FREE trial subscription (every new group starts on trial) ──
        int trialDays = groupSetup.getConfig("trial_days", 30);
        groupSetup.createTrialSubscription(saved.getId(), trialDays);

        // ── Add creator as group ADMIN member ─────────────────────────────────
        String memberNumber = groupSetup.nextMemberNumber(saved.getId());
        groupSetup.createFirstMember(saved.getId(), createdByUserId, memberNumber,
                request.sharesMode() ? request.minimumShares() : 1);

        log.info("Group created: id={} slug={} trial={}days by={}",
                saved.getId(), saved.getSlug(), trialDays, createdByUserId);

        return GroupResponse.from(saved);
    }
}

