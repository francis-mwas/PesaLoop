package com.pesaloop.group.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Output port — group setup side-effects that span multiple tables.
 * CreateGroupUseCase and AddMemberUseCase depend on this, not on JdbcTemplate.
 */
public interface GroupSetupRepository {

    /** Create a FREE trial subscription for a newly created group. */
    void createTrialSubscription(UUID groupId, int trialDays);

    /** Add the group creator as the first ADMIN member. */
    void createFirstMember(UUID groupId, UUID userId,
                            String memberNumber, int sharesOwned);

    /** Generate next member number atomically via DB function. */
    String nextMemberNumber(UUID groupId);

    /** Get group name. */
    Optional<String> findGroupName(UUID groupId);

    /** Get group status. */
    Optional<String> findGroupStatus(UUID groupId);

    /** Get minimum shares for the group. */
    int findMinimumShares(UUID groupId);

    /** Check if a user already exists by phone. */
    Optional<UUID> findUserIdByPhone(String phone);

    /** Check if a user is already a member of the group. */
    Optional<String> findExistingMemberStatus(UUID groupId, UUID userId);

    /** Create a stub user (admin-invited, no password yet). */
    UUID createStubUser(String phone, String fullName, String inviteToken);

    /** Create a member record. */
    void createMember(UUID groupId, UUID userId, String memberNumber,
                      String role, int sharesOwned, String phone);

    /** Get config value (e.g. trial_days). */
    int getConfig(String key, int defaultValue);
}
