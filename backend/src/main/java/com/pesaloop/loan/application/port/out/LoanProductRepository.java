package com.pesaloop.loan.application.port.out;

import com.pesaloop.loan.domain.model.LoanProduct;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanProductRepository {
    LoanProduct save(LoanProduct product);
    Optional<LoanProduct> findById(UUID id);
    List<LoanProduct> findActiveByGroupId(UUID groupId);
}
