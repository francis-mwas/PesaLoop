package com.pesaloop.identity.application.usecase;

import com.pesaloop.identity.application.port.in.RegisterUserPort;
import com.pesaloop.identity.application.port.in.LoginPort;
import com.pesaloop.identity.application.port.in.OtpPort;
import com.pesaloop.identity.application.port.in.InvitePort;

import com.pesaloop.identity.application.port.out.UserRepository;
import com.pesaloop.identity.application.port.out.UserRepository.*;
import com.pesaloop.identity.domain.service.FieldValidationService;
import com.pesaloop.notification.application.usecase.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Application service — all authentication flows.
 * AuthController delegates here. No JDBC in the adapter layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthUseCase implements RegisterUserPort, LoginPort, OtpPort, InvitePort {

    private final UserRepository userRepository;
    private final FieldValidationService validator;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public RegisterResult register(String phoneNumber, String fullName,
                                   String password, String email) {
        String phone = validator.normalizePhone(phoneNumber);

        if (userRepository.existsByPhone(phone)) {
            throw new IllegalStateException("PHONE_EXISTS:This phone number is already registered.");
        }

        UUID userId = userRepository.createUser(phone, email, fullName,
                passwordEncoder.encode(password));

        sendOtp(phone, "REGISTRATION");

        log.info("User registered: userId={} phone={}", userId, phone);
        return new RegisterResult(userId, phone, true);
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    @Transactional
    public void sendOtp(String rawPhone, String purpose) {
        String phone = validator.normalizePhone(rawPhone);

        if (userRepository.countRecentOtpRequests(phone) >= 5) {
            throw new IllegalStateException("Too many OTP requests. Please wait before requesting again.");
        }

        int otp = 100000 + new Random().nextInt(900000);
        userRepository.createOtpRequest(phone, passwordEncoder.encode(String.valueOf(otp)), purpose);
        // Always log OTP — essential for dev/testing when no SMS gateway is configured
        log.info("OTP generated: phone={} purpose={} code={}", phone, purpose, otp);
        smsService.sendOtp(phone, otp, purpose);
    }

    @Transactional
    public void verifyOtp(String rawPhone, String code) {
        String phone = validator.normalizePhone(rawPhone);

        OtpRecord otp = userRepository.findActiveOtp(phone)
                .orElseThrow(() -> new IllegalArgumentException("No active OTP found. Request a new one."));

        if (!passwordEncoder.matches(code, otp.otpHash())) {
            throw new IllegalArgumentException("Invalid or expired OTP.");
        }

        userRepository.consumeOtp(otp.id());
        userRepository.markPhoneVerified(phone);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public LoginResult login(String rawPhone, String password, UUID groupId) {
        String phone = validator.normalizePhone(rawPhone);

        UserRecord user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("Invalid phone or password."));

        if (!"ACTIVE".equals(user.status())) {
            throw new IllegalStateException("Account is not active. Please contact support.");
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("Invalid phone or password.");
        }

        userRepository.recordLogin(user.id());

        List<UserGroupMembership> groups = userRepository.findGroupMemberships(user.id());

        // No group specified — return group list for picker
        if (groupId == null) {
            return LoginResult.groupPicker(user.id(), user.fullName(), groups);
        }

        // Verify membership in requested group
        MemberRoleRecord membership = userRepository.findMembership(user.id(), groupId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "You are not a member of this group."));

        if (!"ACTIVE".equals(membership.status())) {
            throw new IllegalStateException("Your membership in this group is not active.");
        }

        return LoginResult.withToken(user.id(), user.fullName(), groupId,
                membership.role(), groups);
    }

    // ── Invite acceptance ─────────────────────────────────────────────────────

    @Transactional
    public InviteResult acceptInvite(String token, String password) {
        UserRecord user = userRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired invite link. Please ask your admin to resend."));

        userRepository.acceptInvite(user.id(), passwordEncoder.encode(password));
        log.info("Invite accepted: userId={}", user.id());
        return new InviteResult(user.id(), user.fullName());
    }

    public List<UserGroupMembership> getMyGroups(UUID userId) {
        return userRepository.findGroupMemberships(userId);
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public record RegisterResult(UUID userId, String phone, boolean otpSent) {}

    public record LoginResult(
            UUID userId, String fullName,
            UUID groupId, String role,
            boolean requiresGroupSelection,
            List<UserGroupMembership> availableGroups,
            String tokenPayload  // null until token is generated by controller
    ) {
        static LoginResult groupPicker(UUID userId, String fullName, List<UserGroupMembership> groups) {
            return new LoginResult(userId, fullName, null, null, true, groups, null);
        }
        static LoginResult withToken(UUID userId, String fullName, UUID groupId,
                                     String role, List<UserGroupMembership> groups) {
            return new LoginResult(userId, fullName, groupId, role, false, groups, null);
        }
    }

    public record InviteResult(UUID userId, String fullName) {}
}