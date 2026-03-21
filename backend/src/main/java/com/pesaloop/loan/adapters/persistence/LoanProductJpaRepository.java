package com.pesaloop.loan.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanProductJpaRepository extends JpaRepository<LoanProductJpaEntity, UUID> {
    List<LoanProductJpaEntity> findByGroupIdAndActiveTrue(UUID groupId);
}
