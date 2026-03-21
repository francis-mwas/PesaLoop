package com.pesaloop.loan.application.port.out;

import com.pesaloop.loan.domain.model.RepaymentInstallment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepaymentInstallmentRepository {
    RepaymentInstallment save(RepaymentInstallment installment);
    List<RepaymentInstallment> saveAll(List<RepaymentInstallment> installments);
    List<RepaymentInstallment> findByLoanId(UUID loanId);
    Optional<RepaymentInstallment> findNextDueByLoanId(UUID loanId);
    void updateInstallment(RepaymentInstallment installment);
}
