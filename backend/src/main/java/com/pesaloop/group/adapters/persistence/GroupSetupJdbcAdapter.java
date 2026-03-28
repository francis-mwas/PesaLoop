package com.pesaloop.group.adapters.persistence;

import com.pesaloop.group.application.port.out.GroupSetupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GroupSetupJdbcAdapter implements GroupSetupRepository {

    private final JdbcTemplate jdbc;

    @Override
    public void createTrialSubscription(UUID groupId, int trialDays) {
        jdbc.update(
                """
                INSERT INTO group_subscriptions
                    (group_id, plan_code, status, trial_ends_at, billing_cycle, created_at, updated_at)
                VALUES (?, 'FREE', 'TRIAL', NOW()+(? || ' days')::INTERVAL, 'MONTHLY', NOW(), NOW())
                ON CONFLICT (group_id) DO NOTHING
                """,
                groupId, trialDays);
    }

    @Override
    public void createFirstMember(UUID groupId, UUID userId,
                                  String memberNumber, int sharesOwned) {
        jdbc.update(
                """
                INSERT INTO members
                    (id, group_id, user_id, member_number, role, status,
                     shares_owned, savings_balance, arrears_balance, fines_balance,
                     phone_number, joined_on, created_by, created_at, updated_at, version)
                SELECT gen_random_uuid(), ?, u.id, ?, 'ADMIN', 'ACTIVE',
                       ?, 0, 0, 0,
                       u.phone_number, CURRENT_DATE, ?, NOW(), NOW(), 0
                  FROM users u WHERE u.id = ?
                """,
                groupId, memberNumber, sharesOwned, userId, userId);
    }

    @Override
    public String nextMemberNumber(UUID groupId) {
        return jdbc.queryForObject("SELECT next_member_number(?)", String.class, groupId);
    }

    @Override
    public Optional<String> findGroupName(UUID groupId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT name FROM groups WHERE id=?", String.class, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findGroupStatus(UUID groupId) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject("SELECT status FROM groups WHERE id=?", String.class, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public int findMinimumShares(UUID groupId) {
        Integer min = jdbc.queryForObject(
                "SELECT minimum_shares FROM groups WHERE id=?", Integer.class, groupId);
        return min != null ? min : 1;
    }

    @Override
    public Optional<UUID> findUserIdByPhone(String phone) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id FROM users WHERE phone_number=?", UUID.class, phone));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<String> findExistingMemberStatus(UUID groupId, UUID userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT status FROM members WHERE group_id=? AND user_id=?",
                    String.class, groupId, userId));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public UUID createStubUser(String phone, String fullName, String inviteToken) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO users (id,phone_number,full_name,password_hash,status,
                    account_type,kyc_level,phone_verified,email_verified,
                    invite_token,invite_expires_at,created_at,updated_at,version)
                VALUES (?,?,?,'',  'ACTIVE',
                    'ADMIN_CREATED','NONE',FALSE,FALSE,
                    ?,NOW()+INTERVAL '7 days',NOW(),NOW(),0)
                """,
                id, phone, fullName, inviteToken);
        return id;
    }

    @Override
    public void createMember(UUID groupId, UUID userId, String memberNumber,
                             String role, int sharesOwned, String phone) {
        jdbc.update(
                """
                INSERT INTO members
                    (id, group_id, user_id, member_number, role, status,
                     shares_owned, savings_balance, arrears_balance, fines_balance,
                     phone_number, joined_on, created_by, created_at, updated_at, version)
                VALUES (gen_random_uuid(),?,?,?,?,'ACTIVE',
                        ?,0,0,0,?,CURRENT_DATE,?,NOW(),NOW(),0)
                """,
                groupId, userId, memberNumber, role, sharesOwned, phone, userId);
    }

    @Override
    public int getConfig(String key, int defaultValue) {
        try {
            Integer val = jdbc.queryForObject(
                    "SELECT config_value::INT FROM platform_config WHERE config_key=? AND group_id IS NULL",
                    Integer.class, key);
            return val != null ? val : defaultValue;
        } catch (Exception e) { return defaultValue; }
    }
}