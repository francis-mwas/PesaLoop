package com.pesaloop.identity.adapters.web;

import com.pesaloop.identity.adapters.security.JwtTokenProvider;
import com.pesaloop.identity.application.port.in.RegisterUserPort;
import com.pesaloop.identity.application.port.in.LoginPort;
import com.pesaloop.identity.application.port.in.OtpPort;
import com.pesaloop.identity.application.port.in.InvitePort;
import com.pesaloop.identity.application.usecase.AuthUseCase;
import com.pesaloop.identity.application.usecase.AuthUseCase.*;
import com.pesaloop.shared.adapters.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Primary adapter — translates HTTP to AuthUseCase calls.
 * No business logic, no SQL, no PasswordEncoder here.
 * This is a pure HTTP adapter.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUserPort registerPort;
    private final LoginPort loginPort;
    private final OtpPort otpPort;
    private final InvitePort invitePort;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody RegisterRequest req) {

        RegisterResult result = registerPort.register(
                req.phoneNumber(), req.fullName(), req.password(), req.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                Map.of("userId", result.userId(), "otpSent", result.otpSent()),
                "Account created. Verify your phone with the OTP sent to " + result.phone()));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@RequestBody Map<String, String> body) {
        otpPort.sendOtp(body.get("phoneNumber"),
                body.getOrDefault("purpose", "LOGIN"));
        return ResponseEntity.ok(ApiResponse.success(null, "OTP sent"));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@RequestBody Map<String, String> body) {
        otpPort.verifyOtp(body.get("phoneNumber"), body.get("code"));
        return ResponseEntity.ok(ApiResponse.success(null, "Phone verified"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody LoginRequest req) {

        LoginResult result = loginPort.login(
                req.phoneNumber(), req.password(),
                req.groupId() != null ? UUID.fromString(req.groupId()) : null);

        if (result.requiresGroupSelection()) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("requiresGroupSelection", true, "availableGroups", result.availableGroups()),
                    "Select a group to continue"));
        }

        String token = jwtTokenProvider.generateToken(
                result.userId(), result.groupId(), result.role());

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("token", token, "userId", result.userId(),
                        "fullName", result.fullName(), "groupId", result.groupId(),
                        "role", result.role(), "availableGroups", result.availableGroups()),
                "Login successful"));
    }

    @GetMapping("/my-groups")
    public ResponseEntity<ApiResponse<Object>> myGroups(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                invitePort.getMyGroups(UUID.fromString(userId))));
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptInvite(
            @RequestBody Map<String, String> body) {

        InviteResult result = invitePort.acceptInvite(
                body.get("token"), body.get("password"));

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("userId", result.userId(), "fullName", result.fullName()),
                "Account activated. You can now log in."));
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank String phoneNumber,
            @NotBlank String fullName,
            @NotBlank String password,
            String email
    ) {}

    public record LoginRequest(
            @NotBlank String phoneNumber,
            @NotBlank String password,
            String groupId
    ) {}
}
