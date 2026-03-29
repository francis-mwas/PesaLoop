package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.group.domain.model.Member;
import com.pesaloop.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter — implements MemberRepository using Spring Data JPA.
 * JdbcTemplate is added only for the cross-table fullName lookup
 * (members JOIN users) which JPA cannot do without a User entity.
 */
@Repository
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpa;
    private final JdbcTemplate jdbc;

    @Override
    public Member save(Member member) {
        if (member.getId() != null) {
            return jpa.findById(member.getId()).map(existing -> {
                existing.setSavingsBalance(member.getSavingsBalance().getAmount());
                existing.setArrearsBalance(member.getArrearsBalance() != null
                        ? member.getArrearsBalance().getAmount()
                        : java.math.BigDecimal.ZERO);
                existing.setSharesOwned(member.getSharesOwned());
                existing.setStatus(member.getStatus());
                existing.setRole(member.getRole());
                return toDomain(jpa.save(existing));
            }).orElseGet(() -> toDomain(jpa.save(toEntity(member))));
        }
        return toDomain(jpa.save(toEntity(member)));
    }

    @Override
    public Optional<Member> findById(UUID memberId) {
        return jpa.findById(memberId).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByGroupIdAndUserId(UUID groupId, UUID userId) {
        return jpa.findByGroupIdAndUserId(groupId, userId).map(this::toDomain);
    }

    @Override
    public Optional<Member> findByGroupIdAndMemberNumber(UUID groupId, String memberNumber) {
        return jpa.findByGroupIdAndMemberNumber(groupId, memberNumber).map(this::toDomain);
    }

    @Override
    public List<Member> findActiveByGroupId(UUID groupId) {
        return jpa.findActiveByGroupId(groupId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Member> findByGroupId(UUID groupId) {
        return jpa.findByGroupIdOrderByMemberNumber(groupId).stream().map(this::toDomain).toList();
    }

    @Override
    public int countActiveByGroupId(UUID groupId) {
        return jpa.countActiveByGroupId(groupId);
    }

    @Override
    public int sumSharesByGroupId(UUID groupId) {
        return jpa.sumSharesByGroupId(groupId);
    }

    @Override
    public Optional<String> findFullNameById(UUID memberId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT u.full_name FROM users u JOIN members m ON m.user_id = u.id WHERE m.id = ?",
                    String.class, memberId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private Member toDomain(MemberJpaEntity e) {
        return Member.builder()
                .id(e.getId())
                .groupId(e.getGroupId())
                .userId(e.getUserId())
                .memberNumber(e.getMemberNumber())
                .role(e.getRole())
                .status(e.getStatus())
                .sharesOwned(e.getSharesOwned())
                .savingsBalance(Money.ofKes(e.getSavingsBalance()))
                .arrearsBalance(Money.ofKes(e.getArrearsBalance()))
                .finesBalance(Money.ofKes(e.getFinesBalance()))
                .phoneNumber(e.getPhoneNumber())
                .joinedOn(e.getJoinedOn())
                .nextOfKinName(e.getNextOfKinName())
                .nextOfKinPhone(e.getNextOfKinPhone())
                .nextOfKinRelationship(e.getNextOfKinRelationship())
                .build();
    }

    private MemberJpaEntity toEntity(Member d) {
        MemberJpaEntity e = new MemberJpaEntity();
        e.setId(d.getId());
        e.setGroupId(d.getGroupId());
        e.setUserId(d.getUserId());
        e.setMemberNumber(d.getMemberNumber());
        e.setRole(d.getRole());
        e.setStatus(d.getStatus());
        e.setSharesOwned(d.getSharesOwned());
        e.setSavingsBalance(d.getSavingsBalance().getAmount());
        e.setArrearsBalance(d.getArrearsBalance().getAmount());
        e.setFinesBalance(d.getFinesBalance().getAmount());
        e.setPhoneNumber(d.getPhoneNumber());
        e.setJoinedOn(d.getJoinedOn());
        e.setNextOfKinName(d.getNextOfKinName());
        e.setNextOfKinPhone(d.getNextOfKinPhone());
        e.setNextOfKinRelationship(d.getNextOfKinRelationship());
        return e;
    }
}