package com.pesaloop.group.application.dto;

import com.pesaloop.group.domain.model.*;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable response object returned from all Group use cases.
 * Decouples the REST layer from the domain model.
 */
public record GroupResponse(
        UUID id,
        String slug,
        String name,
        String description,
        Set<GroupType> types,
        GroupStatus status,
        String currencyCode,
        ShareConfigDto shareConfig,
        ContributionFrequency contributionFrequency,
        Integer customFrequencyDays,
        String mpesaShortcode,
        String mpesaShortcodeType,
        int gracePeriodDays,
        boolean requiresGuarantorForLoans,
        int maxActiveLoansPerMember,
        Instant createdAt
) {

    public static GroupResponse from(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getSlug(),
                group.getName(),
                group.getDescription(),
                group.getTypes(),
                group.getStatus(),
                group.getCurrencyCode(),
                ShareConfigDto.from(group.getShareConfig()),
                group.getContributionFrequency(),
                group.getCustomFrequencyDays(),
                group.getMpesaShortcode(),
                group.getMpesaShortcodeType(),
                group.getGracePeriodDays(),
                group.isRequiresGuarantorForLoans(),
                group.getMaxActiveLoansPerMember(),
                group.getCreatedAt()
        );
    }

    public record ShareConfigDto(
            String pricePerShare,
            String currency,
            int minimumShares,
            int maximumShares,
            boolean sharesMode,
            boolean allowShareChangeMidYear
    ) {
        public static ShareConfigDto from(ShareConfig config) {
            if (config == null) return null;
            return new ShareConfigDto(
                    config.getPricePerShare().getAmount().toPlainString(),
                    config.getPricePerShare().getCurrencyCode(),
                    config.getMinimumShares(),
                    config.getMaximumShares(),
                    config.isSharesMode(),
                    config.isAllowShareChangeMidYear()
            );
        }
    }
}
