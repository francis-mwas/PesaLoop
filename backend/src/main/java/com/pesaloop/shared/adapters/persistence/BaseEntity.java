package com.pesaloop.shared.adapters.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all JPA entities. Provides:
 * - UUID primary key
 * - Created/updated audit timestamps
 * - Created/updated by (user UUID)
 * - group_id for multi-tenancy (overridable)
 *
 * Implements Persistable<UUID> so Spring Data asks us whether the entity
 * is new rather than guessing from the id/version fields. This is required
 * because domain models generate their own UUIDs before persistence, which
 * would otherwise cause Spring Data to call merge() instead of persist()
 * on brand-new entities (leading to StaleObjectStateException).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * An entity is new if it has never been persisted — i.e. version is null.
     * After the first persist(), Hibernate sets version=0, so isNew()=false
     * on all subsequent saves (which correctly use merge/UPDATE).
     */
    @Override
    @Transient
    public boolean isNew() {
        return version == null;
    }
}