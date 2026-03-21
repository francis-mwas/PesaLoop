package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.RepaymentInstallmentRepository;
import com.pesaloop.loan.domain.model.RepaymentInstallment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secondary adapter — implements RepaymentInstallmentRepository using Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class RepaymentInstallmentRepositoryAdapter implements RepaymentInstallmentRepository {

    private final RepaymentInstallmentJpaRepository jpa;
    private final LoanMapper mapper;

    @Override
    public RepaymentInstallment save(RepaymentInstallment installment) {
        return mapper.toDomain(jpa.save(mapper.toEntity(installment)));
    }

    @Override
    public List<RepaymentInstallment> saveAll(List<RepaymentInstallment> installments) {
        return jpa.saveAll(installments.stream().map(mapper::toEntity).collect(Collectors.toList()))
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<RepaymentInstallment> findByLoanId(UUID loanId) {
        return jpa.findByLoanIdOrderByInstallmentNumber(loanId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public Optional<RepaymentInstallment> findNextDueByLoanId(UUID loanId) {
        return jpa.findNextDueByLoanId(loanId,
                        com.pesaloop.loan.domain.model.RepaymentInstallment.InstallmentStatus.PENDING,
                        com.pesaloop.loan.domain.model.RepaymentInstallment.InstallmentStatus.PARTIAL,
                        com.pesaloop.loan.domain.model.RepaymentInstallment.InstallmentStatus.OVERDUE)
                .stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public void updateInstallment(RepaymentInstallment installment) {
        jpa.save(mapper.toEntity(installment));
    }
}