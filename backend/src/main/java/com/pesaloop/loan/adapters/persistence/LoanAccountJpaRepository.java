package com.pesaloop.loan.adapters.persistence;

import com.pesaloop.loan.domain.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanAccountJpaRepository extends JpaRepository<LoanAccountJpaEntity, UUID> {

    Optional<LoanAccountJpaEntity> findByGroupIdAndLoanReference(UUID groupId, String reference);

    List<LoanAccountJpaEntity> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    List<LoanAccountJpaEntity> findByMemberIdAndStatus(UUID memberId, LoanStatus status);

    @Query("SELECT la FROM LoanAccountJpaEntity la WHERE la.groupId = :groupId AND la.status = :status")
    List<LoanAccountJpaEntity> findActiveByGroupId(
            @org.springframework.data.repository.query.Param("groupId") UUID groupId,
            @org.springframework.data.repository.query.Param("status") LoanStatus status
    );

    @Query("SELECT COUNT(la) FROM LoanAccountJpaEntity la WHERE la.memberId = :memberId AND la.productId = :productId AND la.status IN (:s1, :s2, :s3)")
    int countActiveByMemberIdAndProductId(
            @org.springframework.data.repository.query.Param("memberId") UUID memberId,
            @org.springframework.data.repository.query.Param("productId") UUID productId,
            @org.springframework.data.repository.query.Param("s1") LoanStatus s1,
            @org.springframework.data.repository.query.Param("s2") LoanStatus s2,
            @org.springframework.data.repository.query.Param("s3") LoanStatus s3
    );
}
