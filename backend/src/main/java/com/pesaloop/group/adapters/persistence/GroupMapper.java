package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.domain.model.*;
import com.pesaloop.shared.domain.Money;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts between GroupJpaEntity (infrastructure) and Group domain model.
 * MapStruct generates the implementation at compile time — no reflection overhead.
 */
@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "types", expression = "java(toGroupTypes(entity.getGroupTypes()))")
    @Mapping(target = "shareConfig", expression = "java(toShareConfig(entity))")
    @Mapping(target = "mgrPayoutConfig", expression = "java(toMgrConfig(entity))")
    @Mapping(target = "financialYearStart", ignore = true)
    @Mapping(target = "financialYearEnd", ignore = true)
    Group toDomain(GroupJpaEntity entity);

    @Mapping(target = "groupTypes", expression = "java(fromGroupTypes(group.getTypes()))")
    @Mapping(target = "sharePriceAmount", expression = "java(group.getShareConfig().getPricePerShare().getAmount())")
    @Mapping(target = "sharePriceCurrency", expression = "java(group.getShareConfig().getPricePerShare().getCurrencyCode())")
    @Mapping(target = "minimumShares", expression = "java(group.getShareConfig().getMinimumShares())")
    @Mapping(target = "maximumShares", expression = "java(group.getShareConfig().getMaximumShares())")
    @Mapping(target = "sharesMode", expression = "java(group.getShareConfig().isSharesMode())")
    @Mapping(target = "allowShareChangeMidYear", expression = "java(group.getShareConfig().isAllowShareChangeMidYear())")
    @Mapping(target = "mgrRotationStrategy", expression = "java(group.getMgrPayoutConfig() != null ? group.getMgrPayoutConfig().getRotationStrategy() : null)")
    @Mapping(target = "mgrPayoutTrigger", expression = "java(group.getMgrPayoutConfig() != null ? group.getMgrPayoutConfig().getPayoutTrigger() : null)")
    @Mapping(target = "mgrWaitForAll", expression = "java(group.getMgrPayoutConfig() != null && group.getMgrPayoutConfig().isWaitForAllBeforePayout())")
    @Mapping(target = "mgrAllowPositionSwaps", expression = "java(group.getMgrPayoutConfig() != null && group.getMgrPayoutConfig().isAllowPositionSwaps())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    GroupJpaEntity toEntity(Group group);

    default Set<GroupType> toGroupTypes(String[] types) {
        if (types == null || types.length == 0) return EnumSet.of(GroupType.TABLE_BANKING);
        return Arrays.stream(types)
                .map(GroupType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(GroupType.class)));
    }

    default String[] fromGroupTypes(Set<GroupType> types) {
        if (types == null) return new String[]{"TABLE_BANKING"};
        return types.stream().map(Enum::name).toArray(String[]::new);
    }

    default ShareConfig toShareConfig(GroupJpaEntity e) {
        return ShareConfig.builder()
                .pricePerShare(Money.of(e.getSharePriceAmount(), e.getSharePriceCurrency()))
                .minimumShares(e.getMinimumShares())
                .maximumShares(e.getMaximumShares())
                .sharesMode(e.isSharesMode())
                .allowShareChangeMidYear(e.isAllowShareChangeMidYear())
                .build();
    }

    default MgrPayoutConfig toMgrConfig(GroupJpaEntity e) {
        if (e.getMgrRotationStrategy() == null) return null;
        return MgrPayoutConfig.builder()
                .rotationStrategy(e.getMgrRotationStrategy())
                .payoutTrigger(e.getMgrPayoutTrigger())
                .waitForAllBeforePayout(e.isMgrWaitForAll())
                .allowPositionSwaps(e.isMgrAllowPositionSwaps())
                .allowMultiplePayoutsPerYear(false)
                .build();
    }
}
