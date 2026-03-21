package com.pesaloop.loan.adapters.web;

import com.pesaloop.loan.application.port.in.DisbursementPort;
import com.pesaloop.loan.application.usecase.DisbursementService;
import com.pesaloop.loan.application.usecase.DisbursementService.*;
import com.pesaloop.shared.domain.TenantContext;
import com.pesaloop.shared.adapters.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Disbursement instruction queue.
 *
 * The treasurer's "to-do list" for outgoing payments.
 * Everything here represents money the group needs to send,
 * but PesaLoop does not initiate the transfer — the treasurer does it
 * manually from the group's M-Pesa, then comes back to confirm.
 *
 * Endpoints:
 *   GET  /disbursements/pending          → treasurer sees what to pay today
 *   POST /disbursements/{id}/confirm     → treasurer enters M-Pesa ref after paying
 *   POST /disbursements/{id}/cancel      → cancel an instruction (e.g. if loan cancelled)
 */
@RestController
@RequestMapping("/api/v1/disbursements")
@RequiredArgsConstructor
public class DisbursementController {

    private final DisbursementPort disbursementService;

    /**
     * GET /api/v1/disbursements/pending
     * Returns all pending disbursement instructions for this group.
     * The treasurer uses this as their daily payment queue.
     *
     * Each instruction shows:
     *   - Who to pay
     *   - How much
     *   - Their M-Pesa phone
     *   - The suggested account reference to enter
     *   - When the instruction expires
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<List<DisbursementInstruction>>> getPending() {
        UUID groupId = TenantContext.getGroupId();
        List<DisbursementInstruction> instructions =
                disbursementService.getPendingInstructions(groupId);
        return ResponseEntity.ok(ApiResponse.success(instructions,
                instructions.isEmpty()
                        ? "No pending disbursements"
                        : instructions.size() + " disbursement(s) awaiting confirmation"));
    }

    /**
     * POST /api/v1/disbursements/{id}/confirm
     * Treasurer confirms that the manual M-Pesa transfer was made.
     * Provide the M-Pesa confirmation code from the treasurer's phone.
     *
     * This activates the loan, marks the MGR cycle as paid, etc.
     * The M-Pesa reference is stored for audit purposes.
     *
     * Request body:
     *   {
     *     "mpesaReference": "SHK7N2A1B3",  ← from treasurer's M-Pesa confirmation SMS
     *     "notes": "Sent via Equity Bank"   ← optional
     *   }
     */
    @PostMapping("/{instructionId}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<DisbursementConfirmationResult>> confirm(
            @PathVariable UUID instructionId,
            @RequestBody ConfirmRequest req,
            @AuthenticationPrincipal String userId) {

        DisbursementConfirmationResult result = disbursementService.confirmDisbursement(
                instructionId,
                req.mpesaReference(),
                req.notes(),
                UUID.fromString(userId)
        );

        return ResponseEntity.ok(ApiResponse.success(result,
                "Disbursement confirmed. " + result.effect()));
    }

    /**
     * POST /api/v1/disbursements/{id}/cancel
     * Cancels a pending instruction (e.g. if the loan application was withdrawn).
     * The loan is reverted to APPROVED status so it can be re-issued.
     */
    @PostMapping("/{instructionId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID instructionId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String userId) {

        disbursementService.cancelInstruction(
                instructionId,
                body.getOrDefault("reason", "Cancelled by admin"),
                UUID.fromString(userId)
        );

        return ResponseEntity.ok(ApiResponse.success(null,
                "Disbursement instruction cancelled. Loan returned to APPROVED status."));
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record ConfirmRequest(
            String mpesaReference,   // optional — treasurer might use bank instead
            String notes
    ) {}
}
