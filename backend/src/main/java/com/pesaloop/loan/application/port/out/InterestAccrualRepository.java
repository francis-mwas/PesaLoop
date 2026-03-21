package com.pesaloop.loan.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output port — all persistence for the nightly interest accrual job.
 * InterestAccrualScheduler depends on this, not on JdbcTemplate.
 */
public interface InterestAccrualRepository {

    /** All active non-flat-rate loans eligible for interest accrual. */
    List<LoanAccrualRow> findActiveAccrualLoans();

    /** Adds accrued interest to the loan account and updates the next installment. */
    void recordAccruedInterest(UUID loanId, UUID groupId, BigDecimal interestAmount,
                                LocalDate accrualDate, String accrualFrequency);

    record LoanAccrualRow(
            UUID loanId,
            UUID groupId,
            BigDecimal principalBalance,
            BigDecimal accruedInterest,
            LocalDate disbursementDate,
            String interestType,
            String accrualFrequency,
            BigDecimal interestRate,
            Integer customAccrualDays
    ) {}
}
