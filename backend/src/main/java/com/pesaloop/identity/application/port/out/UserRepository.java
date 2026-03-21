package com.pesaloop.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — user and authentication persistence.
 * Declared in domain. Implemented in identity/adapters/persistence.
 */
public interface UserRepository {

    boolean existsByPhone(String phoneNumber);

    UUID createUser(String phoneNumber, String email, String fullName,
                    String passwordHash);

    Optional<UserRecord> findByPhone(String phoneNumber);

    Optional<UserRecord> findByInviteToken(String token);

    void markPhoneVerified(String phoneNumber);

    void recordLogin(UUID userId);

    void acceptInvite(UUID userId, String passwordHash);

    /** OTP operations */
    void createOtpRequest(String phoneNumber, String otpHash, String purpose);

    Optional<OtpRecord> findActiveOtp(String phoneNumber);

    int countRecentOtpRequests(String phoneNumber);

    void consumeOtp(UUID otpId);

    /** Group membership for login — multi-group picker */
    List<UserGroupMembership> findGroupMemberships(UUID userId);

    Optional<MemberRoleRecord> findMembership(UUID userId, UUID groupId);

    record UserRecord(UUID id, String passwordHash, String fullName, String status) {}
    record OtpRecord(UUID id, String otpHash) {}
    record MemberRoleRecord(String role, String status) {}
    record UserGroupMembership(UUID groupId, String groupName, String groupSlug,
                               String role, String memberNumber) {}
}
