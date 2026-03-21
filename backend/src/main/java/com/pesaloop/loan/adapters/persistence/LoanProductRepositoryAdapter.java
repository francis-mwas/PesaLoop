package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.application.port.out.LoanProductRepository;
import com.pesaloop.loan.domain.model.LoanProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Secondary adapter — implements LoanProductRepository using Spring Data JPA. */
@Repository
@RequiredArgsConstructor
public class LoanProductRepositoryAdapter implements LoanProductRepository {

    private final LoanProductJpaRepository jpa;
    private final LoanMapper mapper;

    @Override
    public LoanProduct save(LoanProduct product) {
        LoanProductJpaEntity entity = mapper.toEntity(product);
        entity.setGroupId(product.getGroupId());
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<LoanProduct> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<LoanProduct> findActiveByGroupId(UUID groupId) {
        return jpa.findByGroupIdAndActiveTrue(groupId)
                .stream().map(mapper::toDomain).collect(Collectors.toList());
    }
}
