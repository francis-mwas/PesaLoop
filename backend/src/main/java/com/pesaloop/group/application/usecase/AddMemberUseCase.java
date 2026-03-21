package com.pesaloop.group.application.usecase;

import com.pesaloop.group.application.port.in.AddMemberPort;

import com.pesaloop.group.application.port.out.GroupSetupRepository;
import com.pesaloop.group.application.port.out.MemberRepository;
import com.pesaloop.identity.domain.service.FieldValidationService;
import com.pesaloop.notification.application.usecase.SmsService;
import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Adds a member to a group.
 *
 * Three cases:
 *   1. User already exists and is a current member → error
 *   2. User already exists by phone → link to group as new member
 *   3. User doesn't exist → create stub user + invite token + send SMS
 *
 * All persistence via domain ports — no JDBC here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddMemberUseCase implements AddMemberPort {

    private final GroupSetupRepository groupSetup;
    private final MemberRepository memberRepository;
    private final FieldValidationService validator;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    @Transactional
    public AddMemberResponse execute(AddMemberRequest req, UUID addedByUserId) {

        UUID groupId = TenantContext.getGroupId();

        // ── 1. Validate group is active ───────────────────────────────────────
        String groupStatus = groupSetup.findGroupStatus(groupId).orElse("UNKNOWN");
        if (!"ACTIVE".equals(groupStatus) && !"PENDING_SETUP".equals(groupStatus)) {
            throw new IllegalStateException("Group is not active");
        }

        // ── 2. Validate phone ─────────────────────────────────────────────────
        String normalizedPhone = validator.normalizePhone(req.phoneNumber());

        // ── 3. Validate shares ────────────────────────────────────────────────
        int minShares = groupSetup.findMinimumShares(groupId);
        int shares = req.sharesOwned() != null ? req.sharesOwned() : minShares;
        if (shares < minShares) {
            throw new IllegalArgumentException(
                    "Minimum shares for this group is " + minShares);
        }

        // ── 4. Resolve user ───────────────────────────────────────────────────
        UserResolution userRes = resolveOrCreateUser(normalizedPhone, req, groupId);

        // ── 5. Check for duplicate membership ────────────────────────────────
        groupSetup.findExistingMemberStatus(groupId, userRes.userId()).ifPresent(status -> {
            if ("ACTIVE".equals(status)) {
                throw new IllegalStateException(normalizedPhone + " is already an active member of this group");
            }
            throw new IllegalStateException(
                    normalizedPhone + " was previously a member (status: " + status + "). Contact support to reactivate.");
        });

        // ── 6. Generate member number ─────────────────────────────────────────
        String memberNumber = groupSetup.nextMemberNumber(groupId);

        // ── 7. Create member record ───────────────────────────────────────────
        UUID memberId = UUID.randomUUID();
        groupSetup.createMember(groupId, userRes.userId(), memberNumber,
                req.role().name(), shares, normalizedPhone);

        // ── 8. Notify ─────────────────────────────────────────────────────────
        String groupName = groupSetup.findGroupName(groupId).orElse("the group");
        String message = userRes.isNewUser()
                ? "New member created. Invite SMS sent to " + normalizedPhone + " to set their password."
                : normalizedPhone + " already has a PesaLoop account. Added as " + req.role().name() + " of " + groupName + ".";

        log.info("Member added: memberId={} memberNo={} groupId={} phone={} isNew={} by={}",
                memberId, memberNumber, groupId, normalizedPhone, userRes.isNewUser(), addedByUserId);

        return new AddMemberResponse(memberId, userRes.userId(), memberNumber,
                req.fullName(), normalizedPhone, req.role(),
                userRes.isNewUser(), userRes.isNewUser(), message);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserResolution resolveOrCreateUser(String phone, AddMemberRequest req, UUID groupId) {
        return groupSetup.findUserIdByPhone(phone)
                .map(id -> new UserResolution(id, false))
                .orElseGet(() -> {
                    String inviteToken = UUID.randomUUID().toString().replace("-", "");
                    UUID userId = groupSetup.createStubUser(phone, req.fullName(), inviteToken);
                    log.info("Stub user created: userId={} phone={}", userId, phone);
                    String groupName = groupSetup.findGroupName(groupId).orElse("your group");
                    smsService.sendInvite(phone, groupName, inviteToken);
                    return new UserResolution(userId, true);
                });
    }

    private record UserResolution(UUID userId, boolean isNewUser) {}

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record AddMemberRequest(
            String phoneNumber, String fullName,
            com.pesaloop.group.domain.model.MemberRole role,
            Integer sharesOwned, String email, String nationalId,
            String nextOfKinName, String nextOfKinPhone, String nextOfKinRelationship
    ) {}

    public record AddMemberResponse(
            UUID memberId, UUID userId, String memberNumber,
            String fullName, String phoneNumber,
            com.pesaloop.group.domain.model.MemberRole role,
            boolean isNewUser, boolean inviteSent, String message
    ) {}
}
