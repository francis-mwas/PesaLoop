package com.pesaloop.group.application.port.out;

import com.pesaloop.group.domain.model.Group;
import com.pesaloop.group.domain.model.GroupStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (output port) for Group persistence.
 * The domain defines this interface — infrastructure implements it.
 * No JPA or Spring imports here.
 */
public interface GroupRepository {
    Group save(Group group);
    Optional<Group> findById(UUID id);
    Optional<Group> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Group> findByStatus(GroupStatus status);
    List<Group> findByCreatedBy(UUID userId);
}
