package com.pesaloop.group.adapters.web;

import com.pesaloop.group.application.dto.CreateGroupRequest;
import com.pesaloop.group.application.dto.GroupResponse;
import com.pesaloop.group.application.port.in.CreateGroupPort;
import com.pesaloop.group.application.port.out.GroupRepository;
import com.pesaloop.group.application.port.in.UpdateShareConfigPort;
import com.pesaloop.group.application.usecase.UpdateShareConfigUseCase.UpdateShareConfigRequest;
import com.pesaloop.group.application.usecase.UpdateShareConfigUseCase.GroupShareConfigResponse;
import com.pesaloop.group.application.usecase.CreateGroupUseCase;

import com.pesaloop.shared.adapters.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final CreateGroupPort createGroupUseCase;
    private final GroupRepository groupRepository;
    private final UpdateShareConfigPort updateShareConfigUseCase;

    /**
     * POST /api/v1/groups
     * Creates a new Chamaa group. The authenticated user becomes the first admin.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal String userId) {

        GroupResponse response = createGroupUseCase.execute(request, UUID.fromString(userId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Group created successfully"));
    }

    /**
     * GET /api/v1/groups/{groupId}
     * Returns group profile. Must be a member of the group.
     */
    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','SECRETARY','MEMBER','AUDITOR')")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(@PathVariable UUID groupId) {
        return groupRepository.findById(groupId)
                .map(GroupResponse::from)
                .map(g -> ResponseEntity.ok(ApiResponse.success(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/groups/check-slug?slug=wanjiku-welfare
     * Public endpoint — checks if a slug is available before group creation.
     */
    @GetMapping("/check-slug")
    public ResponseEntity<ApiResponse<Boolean>> checkSlug(@RequestParam String slug) {
        boolean available = !groupRepository.existsBySlug(slug.toLowerCase());
        return ResponseEntity.ok(ApiResponse.success(available));
    }

    /**
     * PUT /api/v1/groups/{groupId}/share-config
     * Updates the share configuration. Admin only.
     * Changes take effect from the next contribution cycle.
     */
    @PutMapping("/{groupId}/share-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GroupShareConfigResponse>> updateShareConfig(
            @PathVariable UUID groupId,
            @RequestBody UpdateShareConfigRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal String userId) {

        GroupShareConfigResponse result = updateShareConfigUseCase.execute(
                groupId, request, java.util.UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }
}
