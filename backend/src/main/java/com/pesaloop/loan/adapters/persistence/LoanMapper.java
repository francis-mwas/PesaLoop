package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.domain.model.LoanAccount;
import com.pesaloop.loan.domain.model.LoanProduct;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import com.pesaloop.shared.domain.Money;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.ArrayList;

@Mapper(componentModel = "spring")
public interface LoanMapper {

    // ── LoanAccount ───────────────────────────────────────────────────────────

    @Mapping(target = "loanReference",         source = "loanReference")
    @Mapping(target = "principalAmount",      expression = "java(money(e.getPrincipalAmount(), e.getCurrencyCode()))")
    @Mapping(target = "totalInterestCharged", expression = "java(money(e.getTotalInterestCharged(), e.getCurrencyCode()))")
    @Mapping(target = "principalBalance",     expression = "java(money(e.getPrincipalBalance(), e.getCurrencyCode()))")
    @Mapping(target = "accruedInterest",      expression = "java(money(e.getAccruedInterest(), e.getCurrencyCode()))")
    @Mapping(target = "totalInterestRepaid",  expression = "java(money(e.getTotalInterestRepaid(), e.getCurrencyCode()))")
    @Mapping(target = "totalPrincipalRepaid", expression = "java(money(e.getTotalPrincipalRepaid(), e.getCurrencyCode()))")
    @Mapping(target = "penaltyBalance",       expression = "java(money(e.getPenaltyBalance(), e.getCurrencyCode()))")
    @Mapping(target = "guarantorMemberIds",   expression = "java(new java.util.ArrayList<>())")
    LoanAccount toDomain(LoanAccountJpaEntity e);

    @Mapping(target = "currencyCode",         constant = "KES")
    @Mapping(target = "principalAmount",      expression = "java(amt(d.getPrincipalAmount()))")
    @Mapping(target = "totalInterestCharged", expression = "java(amt(d.getTotalInterestCharged()))")
    @Mapping(target = "principalBalance",     expression = "java(amt(d.getPrincipalBalance()))")
    @Mapping(target = "accruedInterest",      expression = "java(amt(d.getAccruedInterest()))")
    @Mapping(target = "totalInterestRepaid",  expression = "java(amt(d.getTotalInterestRepaid()))")
    @Mapping(target = "totalPrincipalRepaid", expression = "java(amt(d.getTotalPrincipalRepaid()))")
    @Mapping(target = "penaltyBalance",       expression = "java(amt(d.getPenaltyBalance()))")
    // Entity-only fields not present on the domain model — preserved by JDBC state-transition
    // commands (approve, reject, activate) so we never overwrite them on a plain save.
    @Mapping(target = "applicationNote",      ignore = true)
    @Mapping(target = "rejectionReason",      ignore = true)
    @Mapping(target = "approvedBy",           ignore = true)
    @Mapping(target = "approvedAt",           ignore = true)
    @Mapping(target = "version",              ignore = true)
    LoanAccountJpaEntity toEntity(LoanAccount d);

    // ── LoanProduct ───────────────────────────────────────────────────────────

    @Mapping(target = "minimumAmount", expression = "java(money(e.getMinimumAmount(), \"KES\"))")
    @Mapping(target = "maximumAmount", expression = "java(money(e.getMaximumAmount(), \"KES\"))")
    LoanProduct toDomain(LoanProductJpaEntity e);

    @Mapping(target = "minimumAmount", expression = "java(amt(d.getMinimumAmount()))")
    @Mapping(target = "maximumAmount", expression = "java(amt(d.getMaximumAmount()))")
    @Mapping(target = "version",       ignore = true)
    LoanProductJpaEntity toEntity(LoanProduct d);

    // ── RepaymentInstallment ──────────────────────────────────────────────────

    @Mapping(target = "principalDue",   expression = "java(money(e.getPrincipalDue(), \"KES\"))")
    @Mapping(target = "interestDue",    expression = "java(money(e.getInterestDue(), \"KES\"))")
    @Mapping(target = "totalDue",       expression = "java(money(e.getTotalDue(), \"KES\"))")
    @Mapping(target = "balanceAfter",   expression = "java(money(e.getBalanceAfter(), \"KES\"))")
    @Mapping(target = "principalPaid",  expression = "java(money(e.getPrincipalPaid(), \"KES\"))")
    @Mapping(target = "interestPaid",   expression = "java(money(e.getInterestPaid(), \"KES\"))")
    @Mapping(target = "penaltyPaid",    expression = "java(money(e.getPenaltyPaid(), \"KES\"))")
    RepaymentInstallment toDomain(RepaymentInstallmentJpaEntity e);

    @Mapping(target = "principalDue",   expression = "java(amt(d.getPrincipalDue()))")
    @Mapping(target = "interestDue",    expression = "java(amt(d.getInterestDue()))")
    @Mapping(target = "totalDue",       expression = "java(amt(d.getTotalDue()))")
    @Mapping(target = "balanceAfter",   expression = "java(amt(d.getBalanceAfter()))")
    @Mapping(target = "principalPaid",  expression = "java(amt(d.getPrincipalPaid()))")
    @Mapping(target = "interestPaid",   expression = "java(amt(d.getInterestPaid()))")
    @Mapping(target = "penaltyPaid",    expression = "java(amt(d.getPenaltyPaid()))")
    @Mapping(target = "createdAt",      ignore = true)
    @Mapping(target = "updatedAt",      ignore = true)
    RepaymentInstallmentJpaEntity toEntity(RepaymentInstallment d);

    // ── Helper methods (used in expressions above) ────────────────────────────

    default Money money(BigDecimal amount, String currency) {
        if (amount == null) return Money.ofKes(BigDecimal.ZERO);
        return Money.of(amount, currency != null ? currency : "KES");
    }

    default BigDecimal amt(Money money) {
        if (money == null) return BigDecimal.ZERO;
        return money.getAmount();
    }
}