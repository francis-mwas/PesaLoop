package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output port — group financial ledger queries.
 * MonthlyLedgerUseCase depends on this; LedgerJdbcAdapter implements it.
 */
public interface LedgerRepository {

    /** Returns one row per active member with the four spreadsheet columns for a given month. */
    List<MemberLedgerRow> findMonthlyLedger(UUID groupId,
                                             LocalDate monthStart,
                                             LocalDate monthEnd);

    record MemberLedgerRow(
            UUID memberId,
            String memberNumber,
            String memberName,
            int sharesOwned,
            BigDecimal sharePriceAmount,
            BigDecimal monthlySavings,
            BigDecimal expectedSavings,
            String contributionStatus,
            BigDecimal loanOutstanding,
            BigDecimal totalInterestCharged,
            BigDecimal accruedInterest,
            BigDecimal interestRepaid,
            BigDecimal monthlyRepayment,
            BigDecimal cumulativeSavings,
            BigDecimal arrearsBalance,
            BigDecimal finesBalance,
            String loanReference,
            String loanStatus,
            LocalDate disbursementDate,
            LocalDate loanDueDate
    ) {}
}
