package com.pesaloop.payment.adapters.persistence;

import com.pesaloop.payment.application.port.out.MemberResolutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — resolves a paying member from a C2B paybill bill reference. */
@Repository
@RequiredArgsConstructor
public class MemberResolutionJdbcAdapter implements MemberResolutionRepository {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<MemberRef> findByMemberNumber(UUID groupId, String memberNumber) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,member_number FROM members WHERE group_id=? AND UPPER(member_number)=? AND status='ACTIVE'",
                    (rs, r) -> new MemberRef(UUID.fromString(rs.getString("id")), rs.getString("member_number")),
                    groupId, memberNumber.toUpperCase()));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<MemberRef> findByPhone(UUID groupId, String phone) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,member_number FROM members WHERE group_id=? AND phone_number=? AND status='ACTIVE'",
                    (rs, r) -> new MemberRef(UUID.fromString(rs.getString("id")), rs.getString("member_number")),
                    groupId, phone));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<MemberRef> findByUserPhone(UUID groupId, String phone) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT m.id,m.member_number FROM members m JOIN users u ON u.id=m.user_id WHERE m.group_id=? AND u.phone_number=? AND m.status='ACTIVE'",
                    (rs, r) -> new MemberRef(UUID.fromString(rs.getString("id")), rs.getString("member_number")),
                    groupId, phone));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<MemberRef> findByNamePartial(UUID groupId, String namePart) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT m.id,m.member_number FROM members m JOIN users u ON u.id=m.user_id WHERE m.group_id=? AND LOWER(u.full_name) LIKE LOWER(?) AND m.status='ACTIVE' LIMIT 1",
                    (rs, r) -> new MemberRef(UUID.fromString(rs.getString("id")), rs.getString("member_number")),
                    groupId, "%" + namePart + "%"));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<GroupRef> findGroupByShortcode(String shortcode) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id FROM groups WHERE mpesa_shortcode=? AND status='ACTIVE'",
                    (rs, r) -> new GroupRef(UUID.fromString(rs.getString("id"))),
                    shortcode));
        } catch (Exception e) { return Optional.empty(); }
    }
}
