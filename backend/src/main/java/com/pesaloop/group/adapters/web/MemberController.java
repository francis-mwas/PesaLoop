package com.pesaloop.group.adapters.web;

import com.pesaloop.group.application.port.in.AddMemberPort;
import com.pesaloop.group.application.port.out.MemberQueryRepository;
import com.pesaloop.group.application.port.out.MemberQueryRepository.MemberSummary;
import com.pesaloop.group.application.usecase.AddMemberUseCase.AddMemberRequest;
import com.pesaloop.group.application.usecase.AddMemberUseCase.AddMemberResponse;
import com.pesaloop.group.domain.model.MemberRole;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Primary adapter — HTTP interface for member management.
 * Injects input ports (use case interfaces) and output ports (query repositories).
 * No SQL in this class.
 */
@Slf4j
@Tag(name = "Members", description = "Member management, shares, and profiles")
@RestController
@RequestMapping("/api/v1/groups/{groupId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final AddMemberPort addMemberUseCase;
    private final MemberQueryRepository memberQueryRepository;

    /**
     * POST /api/v1/groups/{groupId}/members
     * Admin adds a new member. Creates user account if phone not yet registered.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AddMemberResponse>> addMember(
            @PathVariable UUID groupId,
            @RequestBody AddMemberBody body,
            @AuthenticationPrincipal String userId) {

        AddMemberRequest req = new AddMemberRequest(
                body.phoneNumber(), body.fullName(),
                body.role() != null ? MemberRole.valueOf(body.role().toUpperCase()) : MemberRole.MEMBER,
                body.sharesOwned(), body.email(), body.nationalId(),
                body.nextOfKinName(), body.nextOfKinPhone(), body.nextOfKinRelationship());

        AddMemberResponse result = addMemberUseCase.execute(req, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, result.message()));
    }

    /**
     * GET /api/v1/groups/{groupId}/members?status=ACTIVE
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR','SECRETARY')")
    public ResponseEntity<ApiResponse<List<MemberSummary>>> listMembers(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "ACTIVE") String status) {

        List<MemberSummary> members = memberQueryRepository
                .findMemberSummariesByGroupId(groupId, status.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    /**
     * GET /api/v1/groups/{groupId}/members/{memberId}
     * Members can view only their own profile.
     */
    @GetMapping("/{memberId}")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR','SECRETARY','MEMBER')")
    public ResponseEntity<ApiResponse<MemberSummary>> getMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @AuthenticationPrincipal String userId) {

        // Members can only view their own profile
        if ("MEMBER".equals(TenantContext.getRole())) {
            UUID callerMemberId = memberQueryRepository
                    .findMemberIdByUserId(UUID.fromString(userId), groupId).orElse(null);
            if (!memberId.equals(callerMemberId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("You can only view your own profile", "FORBIDDEN"));
            }
        }

        return memberQueryRepository.findMemberSummaryById(memberId, groupId)
                .map(m -> ResponseEntity.ok(ApiResponse.success(m)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/v1/groups/{groupId}/members/{memberId}/shares
     * Admin updates a member's share count. Validates against group min/max.
     */
    @PutMapping("/{memberId}/shares")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateShares(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @RequestBody UpdateSharesRequest req,
            @AuthenticationPrincipal String userId) {

        int[] limits = memberQueryRepository.findShareLimits(groupId);
        if (req.shares() < limits[0]) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Minimum shares for this group is " + limits[0], "BELOW_MINIMUM"));
        }
        if (req.shares() > limits[1]) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Maximum shares for this group is " + limits[1], "ABOVE_MAXIMUM"));
        }

        memberQueryRepository.updateShares(memberId, groupId, req.shares(),
                UUID.fromString(userId), req.reason());

        return ResponseEntity.ok(ApiResponse.success(null, "Shares updated to " + req.shares()));
    }

    // ── Request bodies ────────────────────────────────────────────────────────

    public record AddMemberBody(
            @NotNull String phoneNumber,
            @NotNull String fullName,
            String role,
            Integer sharesOwned,
            String email, String nationalId,
            String nextOfKinName, String nextOfKinPhone, String nextOfKinRelationship
    ) {}

    public record UpdateSharesRequest(@Min(1) int shares, String reason) {}
}
