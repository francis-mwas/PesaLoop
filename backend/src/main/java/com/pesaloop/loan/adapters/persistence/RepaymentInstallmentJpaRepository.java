package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.domain.model.RepaymentInstallment.InstallmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepaymentInstallmentJpaRepository
        extends JpaRepository<RepaymentInstallmentJpaEntity, UUID> {

    List<RepaymentInstallmentJpaEntity> findByLoanIdOrderByInstallmentNumber(UUID loanId);

    /**
     * Returns unpaid installments in due-date order — the "next to pay" set.
     * Uses typed enum params instead of string literals so a rename causes
     * a compile error rather than silently returning empty results.
     */
    @Query("""
           SELECT r FROM RepaymentInstallmentJpaEntity r
            WHERE r.loanId = :loanId
              AND r.status IN (:pending, :partial, :overdue)
            ORDER BY r.installmentNumber ASC
           """)
    List<RepaymentInstallmentJpaEntity> findNextDueByLoanId(
            @Param("loanId")   UUID loanId,
            @Param("pending")  InstallmentStatus pending,
            @Param("partial")  InstallmentStatus partial,
            @Param("overdue")  InstallmentStatus overdue
    );
}
