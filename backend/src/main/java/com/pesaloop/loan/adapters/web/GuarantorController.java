package com.pesaloop.loan.adapters.web;

import com.pesaloop.loan.application.port.in.GuarantorPort;
import com.pesaloop.loan.application.usecase.GuarantorUseCase;
import com.pesaloop.loan.application.usecase.GuarantorUseCase.*;
import com.pesaloop.loan.application.port.out.GuarantorRepository;
import com.pesaloop.shared.adapters.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guarantor workflow endpoints.
 *
 * Admin nominates guarantors → guarantors respond → loan advances to approval.
 */
@RestController
@RequestMapping("/api/v1/loans/{loanId}/guarantors")
@RequiredArgsConstructor
public class GuarantorController {

    private final GuarantorPort guarantorUseCase;

    /**
     * GET /api/v1/loans/{loanId}/guarantors
     * View current guarantor status for a loan.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<List<GuarantorRepository.GuarantorRecord>>> list(
            @PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.success(guarantorUseCase.getGuarantors(loanId)));
    }

    /**
     * POST /api/v1/loans/{loanId}/guarantors
     * Admin nominates guarantors for a PENDING_GUARANTOR loan.
     * Body: { "guarantorMemberIds": ["uuid1", "uuid2"] }
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> nominate(
            @PathVariable UUID loanId,
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal String userId) {

        List<UUID> ids = body.getOrDefault("guarantorMemberIds", List.of())
                .stream().map(UUID::fromString).toList();

        if (ids.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Provide at least one guarantorMemberId", "BAD_REQUEST"));
        }

        guarantorUseCase.nominateGuarantors(loanId, ids, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(null,
                "Guarantors nominated. SMS sent to each guarantor."));
    }

    /**
     * PUT /api/v1/loans/{loanId}/guarantors/{guarantorMemberId}
     * Guarantor accepts or declines.
     * Body: { "accepted": true, "note": "Happy to help" }
     *
     * If all required guarantors accept, loan automatically advances to PENDING_APPROVAL.
     */
    @PutMapping("/{guarantorMemberId}")
    @PreAuthorize("hasAnyRole('ADMIN','MEMBER','TREASURER')")
    public ResponseEntity<ApiResponse<GuarantorResponse>> respond(
            @PathVariable UUID loanId,
            @PathVariable UUID guarantorMemberId,
            @RequestBody Map<String, Object> body) {

        boolean accepted = Boolean.TRUE.equals(body.get("accepted"));
        String note = (String) body.get("note");

        GuarantorResponse result = guarantorUseCase.respond(loanId, guarantorMemberId, accepted, note);
        return ResponseEntity.ok(ApiResponse.success(result, result.message()));
    }
}
