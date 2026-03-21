package com.pesaloop.identity.adapters.persistence;

import com.pesaloop.identity.application.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter — implements UserRepository using JDBC.
 * AuthController delegates to AuthUseCase which calls this port.
 * No raw SQL lives in the web adapter or use cases.
 */
@Repository
@RequiredArgsConstructor
public class UserJdbcAdapter implements UserRepository {

    private final JdbcTemplate jdbc;

    @Override
    public boolean existsByPhone(String phoneNumber) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE phone_number=?", Integer.class, phoneNumber);
        return count != null && count > 0;
    }

    @Override
    public UUID createUser(String phoneNumber, String email, String fullName, String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users (id,phone_number,email,full_name,password_hash,status,account_type,kyc_level,phone_verified,email_verified,created_at,updated_at,version) VALUES (?,?,?,?,?,'ACTIVE','SELF_REGISTERED','NONE',FALSE,FALSE,NOW(),NOW(),0)",
                id, phoneNumber, email, fullName, passwordHash);
        return id;
    }

    @Override
    public Optional<UserRecord> findByPhone(String phoneNumber) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,password_hash,full_name,status FROM users WHERE phone_number=?",
                    (rs, row) -> new UserRecord(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            rs.getString("status")),
                    phoneNumber));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public Optional<UserRecord> findByInviteToken(String token) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,password_hash,full_name,status FROM users WHERE invite_token=? AND invite_expires_at>NOW()",
                    (rs, row) -> new UserRecord(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("password_hash"),
                            rs.getString("full_name"),
                            rs.getString("status")),
                    token));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public void markPhoneVerified(String phoneNumber) {
        jdbc.update(
                "UPDATE users SET phone_verified=TRUE, phone_verified_at=NOW(), kyc_level=CASE WHEN kyc_level='NONE' THEN 'PHONE_VERIFIED' ELSE kyc_level END WHERE phone_number=?",
                phoneNumber);
    }

    @Override
    public void recordLogin(UUID userId) {
        jdbc.update("UPDATE users SET last_login_at=NOW() WHERE id=?", userId);
    }

    @Override
    public void acceptInvite(UUID userId, String passwordHash) {
        jdbc.update(
                "UPDATE users SET password_hash=?,account_type='SELF_REGISTERED',invite_token=NULL,invite_accepted_at=NOW(),phone_verified=TRUE,kyc_level='PHONE_VERIFIED' WHERE id=?",
                passwordHash, userId);
    }

    @Override
    public void createOtpRequest(String phoneNumber, String otpHash, String purpose) {
        jdbc.update(
                "INSERT INTO phone_otp_requests (id,phone_number,otp_hash,purpose,expires_at) VALUES (gen_random_uuid(),?,?,?,NOW()+INTERVAL '10 minutes')",
                phoneNumber, otpHash, purpose);
    }

    @Override
    public Optional<OtpRecord> findActiveOtp(String phoneNumber) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id,otp_hash FROM phone_otp_requests WHERE phone_number=? AND expires_at>NOW() AND used_at IS NULL ORDER BY created_at DESC LIMIT 1",
                    (rs, row) -> new OtpRecord(UUID.fromString(rs.getString("id")), rs.getString("otp_hash")),
                    phoneNumber));
        } catch (Exception e) { return Optional.empty(); }
    }

    @Override
    public int countRecentOtpRequests(String phoneNumber) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM phone_otp_requests WHERE phone_number=? AND created_at>NOW()-INTERVAL '1 hour' AND used_at IS NULL",
                Integer.class, phoneNumber);
        return count != null ? count : 0;
    }

    @Override
    public void consumeOtp(UUID otpId) {
        jdbc.update("UPDATE phone_otp_requests SET used_at=NOW() WHERE id=?", otpId);
    }

    @Override
    public List<UserGroupMembership> findGroupMemberships(UUID userId) {
        return jdbc.query(
                "SELECT g.id,g.name,g.slug,m.role,m.member_number FROM members m JOIN groups g ON g.id=m.group_id WHERE m.user_id=? AND m.status='ACTIVE' AND g.status IN ('ACTIVE','PENDING_SETUP') ORDER BY m.joined_on DESC",
                (rs, row) -> new UserGroupMembership(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("role"),
                        rs.getString("member_number")),
                userId);
    }

    @Override
    public Optional<MemberRoleRecord> findMembership(UUID userId, UUID groupId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT role,status FROM members WHERE user_id=? AND group_id=?",
                    (rs, row) -> new MemberRoleRecord(rs.getString("role"), rs.getString("status")),
                    userId, groupId));
        } catch (Exception e) { return Optional.empty(); }
    }
}
