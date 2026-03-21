package com.pesaloop.group.application.usecase;

import com.pesaloop.group.application.port.in.UpdateShareConfigPort;
import com.pesaloop.group.domain.model.Group;
import com.pesaloop.group.domain.model.ShareConfig;
import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.shared.domain.Money;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Updates a group's share configuration.
 *
 * Share config controls:
 *   - pricePerShare: cost of one share (e.g. KES 3,000)
 *   - minimumShares: minimum shares a member must hold (e.g. 1)
 *   - allowShareChangeMidYear: whether price can change during the financial year
 *
 * When share price changes, existing members' expected contribution amounts
 * for future cycles are recalculated. In-progress cycles are not retroactively changed.
 *
 * From the spreadsheet: Daniel Karoki holds 25 shares × KES 3,000 = KES 75,000/month.
 * This use case is what allows an admin to change the share price for the next cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateShareConfigUseCase implements UpdateShareConfigPort {

    private final GroupRepository groupRepository;

    @Transactional
    public GroupShareConfigResponse execute(UUID groupId, UpdateShareConfigRequest request,
                                            UUID updatedByUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (!groupId.equals(TenantContext.getGroupId()))
            throw new IllegalStateException("Access denied");

        ShareConfig currentConfig = group.getShareConfig();

        // Enforce: cannot reduce share price mid-year unless explicitly allowed
        if (!Boolean.TRUE.equals(request.allowMidYearReduction())
                && request.sharePriceAmount() != null
                && currentConfig.getPricePerShare() != null
                && request.sharePriceAmount()
                .compareTo(currentConfig.getPricePerShare().getAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Share price reduction from KES " + currentConfig.getPricePerShare() +
                            " to KES " + request.sharePriceAmount() +
                            " is not allowed mid-year. Set allowMidYearReduction=true to override.");
        }

        ShareConfig newConfig = ShareConfig.builder()
                .pricePerShare(request.sharePriceAmount() != null
                        ? Money.ofKes(request.sharePriceAmount())
                        : currentConfig.getPricePerShare())
                .minimumShares(request.minimumShares() != null
                        ? request.minimumShares()
                        : currentConfig.getMinimumShares())
                .maximumShares(currentConfig.getMaximumShares())
                .sharesMode(currentConfig.isSharesMode())
                .allowShareChangeMidYear(request.allowMidYearReduction() != null
                        ? request.allowMidYearReduction()
                        : currentConfig.isAllowShareChangeMidYear())
                .maxTotalGroupShares(currentConfig.getMaxTotalGroupShares())
                .build();

        group.updateShareConfig(newConfig);
        Group saved = groupRepository.save(group);

        log.info("Share config updated: group={} pricePerShare={} minShares={} by={}",
                groupId,
                newConfig.getPricePerShare(),
                newConfig.getMinimumShares(),
                updatedByUserId);

        return new GroupShareConfigResponse(
                saved.getId(),
                saved.getName(),
                newConfig.getPricePerShare().getAmount(),
                newConfig.getMinimumShares(),
                "Share configuration updated. New amounts take effect from the next cycle."
        );
    }

    public record UpdateShareConfigRequest(
            BigDecimal sharePriceAmount,
            Integer minimumShares,
            Boolean allowMidYearReduction
    ) {}

    public record GroupShareConfigResponse(
            UUID groupId,
            String groupName,
            BigDecimal sharePriceAmount,
            int minimumShares,
            String message
    ) {}
}