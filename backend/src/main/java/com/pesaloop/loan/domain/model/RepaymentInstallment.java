package com.pesaloop.loan.domain.model;

import com.pesaloop.shared.domain.Money;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One installment in a loan's amortization schedule.
 * Generated at disbursement by InterestCalculationService.
 *
 * For BULLET repayment loans, there is only one installment = full principal + interest.
 * For installment loans, there are N installments spread across the repayment period.
 */
@Getter
@Builder
public class RepaymentInstallment {

    private final UUID id;
    private final UUID loanId;
    private final UUID groupId;
    private final int installmentNumber;    // 1, 2, 3, ...
    private final LocalDate dueDate;

    private final Money principalDue;
    private final Money interestDue;
    private final Money totalDue;          // principalDue + interestDue

    // Running balance after this installment is paid
    private final Money balanceAfter;

    // Actual payments
    private Money principalPaid;
    private Money interestPaid;
    private Money penaltyPaid;
    private InstallmentStatus status;
    private java.time.Instant paidAt;
    private String mpesaRef;

    public Money shortfall() {
        Money paid = principalPaid.add(interestPaid);
        return totalDue.subtract(paid);
    }

    public boolean isOverdue() {
        return status == InstallmentStatus.OVERDUE ||
               (status == InstallmentStatus.PENDING && LocalDate.now().isAfter(dueDate));
    }

    public enum InstallmentStatus {
        PENDING,
        PARTIAL,
        PAID,
        OVERDUE,
        WAIVED
    }
}
