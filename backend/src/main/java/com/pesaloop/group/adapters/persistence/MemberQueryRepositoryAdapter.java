package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.application.port.out.MemberQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Secondary adapter — rich member queries joining members, users, and groups. */
@Repository
@RequiredArgsConstructor
public class MemberQueryRepositoryAdapter implements MemberQueryRepository {

    private final JdbcTemplate jdbc;

    private static final String MEMBER_SUMMARY_SELECT = """
            SELECT m.id, m.member_number, u.full_name, u.phone_number,
                   m.role, m.status, m.shares_owned,
                   COALESCE(
                       (SELECT SUM(ce.paid_amount)
                          FROM contribution_entries ce
                         WHERE ce.member_id = m.id AND ce.group_id = m.group_id),
                       m.savings_balance
                   ) AS savings_balance,
                   m.arrears_balance, m.fines_balance, m.joined_on,
                   g.share_price_amount,
                   (SELECT COUNT(*) FROM loan_accounts la
                     WHERE la.member_id = m.id AND la.status = 'ACTIVE') AS active_loans
              FROM members m
              JOIN users u ON u.id = m.user_id
              JOIN groups g ON g.id = m.group_id
            """;

    @Override
    public List<MemberSummary> findMemberSummariesByGroupId(UUID groupId, String statusFilter) {
        String where = "ALL".equals(statusFilter)
                ? " WHERE m.group_id = ? ORDER BY m.member_number"
                : " WHERE m.group_id = ? AND m.status = ? ORDER BY m.member_number";
        Object[] params = "ALL".equals(statusFilter)
                ? new Object[]{groupId}
                : new Object[]{groupId, statusFilter};
        return jdbc.query(MEMBER_SUMMARY_SELECT + where, this::mapRow, params);
    }

    @Override
    public Optional<MemberSummary> findMemberSummaryById(UUID memberId, UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    MEMBER_SUMMARY_SELECT + " WHERE m.id = ? AND m.group_id = ?",
                    this::mapRow, memberId, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<UUID> findMemberIdByUserId(UUID userId, UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id FROM members WHERE user_id = ? AND group_id = ?",
                    UUID.class, userId, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public int[] findShareLimits(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT minimum_shares, maximum_shares FROM groups WHERE id = ?",
                (rs, row) -> new int[]{rs.getInt("minimum_shares"), rs.getInt("maximum_shares")},
                groupId);
    }

    @Override
    public void updateShares(UUID memberId, UUID groupId, int newShares,
                             UUID approvedByUserId, String reason) {
        jdbc.update(
                """
                INSERT INTO member_share_changes
                    (id, group_id, member_id, shares_before, shares_after, effective_date, approved_by, reason)
                SELECT gen_random_uuid(), group_id, id, shares_owned, ?, CURRENT_DATE, ?, ?
                  FROM members WHERE id = ? AND group_id = ?
                """,
                newShares, approvedByUserId, reason, memberId, groupId);
        jdbc.update(
                "UPDATE members SET shares_owned=?, shares_last_changed_on=CURRENT_DATE, updated_at=NOW() WHERE id=? AND group_id=?",
                newShares, memberId, groupId);
    }

    private MemberSummary mapRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MemberSummary(
                UUID.fromString(rs.getString("id")),
                rs.getString("member_number"),
                rs.getString("full_name"),
                rs.getString("phone_number"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getInt("shares_owned"),
                rs.getBigDecimal("savings_balance"),
                rs.getBigDecimal("arrears_balance"),
                rs.getBigDecimal("fines_balance"),
                rs.getObject("joined_on", LocalDate.class),
                rs.getBigDecimal("share_price_amount"),
                rs.getInt("active_loans")
        );
    }
}