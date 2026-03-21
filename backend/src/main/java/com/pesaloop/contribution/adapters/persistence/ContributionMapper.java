package com.pesaloop.contribution.adapters.persistence;

import com.pesaloop.contribution.domain.model.ContributionCycle;
import com.pesaloop.contribution.domain.model.ContributionEntry;
import com.pesaloop.shared.domain.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface ContributionMapper {

    @Mapping(target = "totalExpected",   expression = "java(money(e.getTotalExpectedAmount(), e.getCurrencyCode()))")
    @Mapping(target = "totalCollected",  expression = "java(money(e.getTotalCollectedAmount(), e.getCurrencyCode()))")
    @Mapping(target = "totalArrears",    expression = "java(money(e.getTotalArrearsAmount(), e.getCurrencyCode()))")
    @Mapping(target = "totalFinesIssued",expression = "java(money(e.getTotalFinesAmount(), e.getCurrencyCode()))")
    @Mapping(target = "year",                   source = "financialYear")
    @Mapping(target = "mgrBeneficiaryMemberId", source = "mgrBeneficiaryId")
    @Mapping(target = "mgrPayoutAmount", expression = "java(e.getMgrPayoutAmount() != null ? money(e.getMgrPayoutAmount(), e.getCurrencyCode()) : null)")
    ContributionCycle toDomain(ContributionCycleJpaEntity e);

    @Mapping(target = "totalExpectedAmount",  expression = "java(amt(d.getTotalExpected()))")
    @Mapping(target = "totalCollectedAmount", expression = "java(amt(d.getTotalCollected()))")
    @Mapping(target = "totalArrearsAmount",   expression = "java(amt(d.getTotalArrears()))")
    @Mapping(target = "totalFinesAmount",     expression = "java(amt(d.getTotalFinesIssued()))")
    @Mapping(target = "financialYear",         source = "year")
    @Mapping(target = "mgrBeneficiaryId",     source = "mgrBeneficiaryMemberId")
    @Mapping(target = "mgrPayoutAmount",      expression = "java(d.getMgrPayoutAmount() != null ? amt(d.getMgrPayoutAmount()) : null)")
    @Mapping(target = "version",              ignore = true)
    ContributionCycleJpaEntity toEntity(ContributionCycle d);

    @Mapping(target = "expectedAmount",          expression = "java(money(e.getExpectedAmount(), e.getCurrencyCode()))")
    @Mapping(target = "paidAmount",              expression = "java(money(e.getPaidAmount(), e.getCurrencyCode()))")
    @Mapping(target = "arrearsCarriedForward",   expression = "java(money(e.getArrearsCarriedForward(), e.getCurrencyCode()))")
    ContributionEntry toDomain(ContributionEntryJpaEntity e);

    @Mapping(target = "expectedAmount",          expression = "java(amt(d.getExpectedAmount()))")
    @Mapping(target = "paidAmount",              expression = "java(amt(d.getPaidAmount()))")
    @Mapping(target = "arrearsCarriedForward",   expression = "java(d.getArrearsCarriedForward() != null ? amt(d.getArrearsCarriedForward()) : java.math.BigDecimal.ZERO)")
    @Mapping(target = "version",                 ignore = true)
    ContributionEntryJpaEntity toEntity(ContributionEntry d);

    default Money money(BigDecimal amount, String currency) {
        if (amount == null) return Money.ofKes(BigDecimal.ZERO);
        return Money.of(amount, currency != null ? currency : "KES");
    }

    default BigDecimal amt(Money money) {
        return money != null ? money.getAmount() : BigDecimal.ZERO;
    }
}
