package com.pesaloop.contribution.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Output port — member financial statement queries (cross-context join). */
public interface MemberStatementRepository {

    MemberProfile findMemberProfile(UUID memberId, UUID groupId);

    List<ContributionLine> findContributions(UUID memberId, UUID groupId);

    List<LoanLine> findLoans(UUID memberId, UUID groupId);

    List<RepaymentLine> findRepayments(UUID memberId, UUID groupId);

    List<LoanBookEntry> findActiveLoanBook(UUID groupId);

    record MemberProfile(String memberNumber, String fullName, String phone,
                         int sharesOwned, BigDecimal sharePriceAmount,
                         BigDecimal savingsBalance, BigDecimal arrearsBalance,
                         BigDecimal finesBalance, LocalDate joinedOn) {}

    record ContributionLine(int cycleNumber, int year, LocalDate dueDate,
                            BigDecimal expectedAmount, BigDecimal paidAmount,
                            String status, java.time.Instant fullyPaidAt) {}

    record LoanLine(String loanReference, String status, String productName,
                    BigDecimal principalAmount, BigDecimal totalInterestCharged,
                    BigDecimal principalBalance, BigDecimal accruedInterest,
                    BigDecimal totalPrincipalRepaid, BigDecimal totalInterestRepaid,
                    LocalDate disbursementDate, LocalDate dueDate,
                    java.time.Instant settledAt) {}

    record RepaymentLine(BigDecimal amount, String paymentMethod,
                         String mpesaReference, String narration,
                         java.time.Instant recordedAt, String loanReference) {}

    record LoanBookEntry(UUID loanId, String loanReference, String status,
                         UUID memberId, String memberNumber, String memberName,
                         String memberPhone, String productName, String interestType,
                         BigDecimal principalAmount, BigDecimal principalBalance,
                         BigDecimal accruedInterest, BigDecimal penaltyBalance,
                         BigDecimal totalOutstanding, LocalDate disbursementDate,
                         LocalDate dueDate, boolean isOverdue) {}
}
