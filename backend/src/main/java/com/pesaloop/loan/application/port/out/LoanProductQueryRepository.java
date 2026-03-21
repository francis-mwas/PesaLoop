package com.pesaloop.loan.application.port.out;

import java.util.UUID;

/** Output port — loan product management queries. */
public interface LoanProductQueryRepository {

    int countActiveLoansUnderProduct(UUID productId);

    void updateProduct(UUID productId, UUID groupId,
                       String name, String description,
                       String interestType, String accrualFrequency,
                       java.math.BigDecimal interestRate,
                       java.math.BigDecimal minAmount, java.math.BigDecimal maxAmount,
                       java.math.BigDecimal maxMultipleOfSavings,
                       Integer maxRepaymentPeriods, String repaymentFrequency,
                       Boolean requiresGuarantor, Boolean requiresZeroArrears,
                       java.math.BigDecimal lateRepaymentPenaltyRate,
                       Boolean active);

    void deactivateProduct(UUID productId, UUID groupId);
}
