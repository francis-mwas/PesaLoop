package com.pesaloop.group.application.dto;

import com.pesaloop.group.domain.model.ContributionFrequency;
import com.pesaloop.group.domain.model.GroupType;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateGroupRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @Size(max = 80) String slug,
        @NotNull @NotEmpty List<GroupType> groupTypes,
        @NotBlank @Size(min = 3, max = 3) String currencyCode,
        boolean sharesMode,
        @PositiveOrZero double sharePriceAmount,
        @Min(1) int minimumShares,
        @Min(1) int maximumShares,
        boolean allowShareChangeMidYear,
        @PositiveOrZero double fixedContributionAmount,
        @NotNull ContributionFrequency contributionFrequency,
        Integer customFrequencyDays
) {}
