package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.domain.model.Group;
import com.pesaloop.group.domain.model.GroupStatus;
import com.pesaloop.group.application.port.out.GroupRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter implementing the GroupRepository domain port.
 * The domain only knows about GroupRepository (the port) — never this class.
 */
@Repository
@RequiredArgsConstructor
public class GroupRepositoryAdapter implements GroupRepository {

    private final GroupJpaRepository jpaRepository;
    private final GroupMapper mapper;
    private final EntityManager entityManager;

    @Override
    public Group save(Group group) {
        GroupJpaEntity entity = mapper.toEntity(group);
        GroupJpaEntity saved = jpaRepository.save(entity);
        // Flush immediately so the INSERT reaches the DB before any JDBC calls
        // (e.g. createTrialSubscription uses JdbcTemplate which bypasses the JPA session)
        entityManager.flush();
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Group> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Group> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug).map(mapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public List<Group> findByStatus(GroupStatus status) {
        return jpaRepository.findAll().stream()
                .filter(e -> e.getStatus() == status)
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> findByCreatedBy(UUID userId) {
        return jpaRepository.findByCreatedBy(userId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}