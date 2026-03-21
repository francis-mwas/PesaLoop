package com.pesaloop.group.adapters.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface GroupJpaRepository extends JpaRepository<GroupJpaEntity, UUID> {

    Optional<GroupJpaEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT g FROM GroupJpaEntity g WHERE g.createdBy = :userId ORDER BY g.createdAt DESC")
    java.util.List<GroupJpaEntity> findByCreatedBy(UUID userId);
}
