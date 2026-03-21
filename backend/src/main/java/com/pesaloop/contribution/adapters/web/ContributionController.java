package com.pesaloop.contribution.adapters.web;

import com.pesaloop.contribution.application.dto.ContributionDtos.*;
import com.pesaloop.contribution.application.port.in.GetCycleSummaryPort;
import com.pesaloop.contribution.application.port.in.InitiateStkPushPort;
import com.pesaloop.contribution.application.port.in.RecordContributionPort;
import com.pesaloop.contribution.application.port.out.ContributionCycleManagementRepository;
import com.pesaloop.contribution.application.port.out.ContributionCycleManagementRepository.CycleSummaryRow;
import com.pesaloop.contribution.application.port.out.ContributionCycleManagementRepository.OpenCycleResult;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Primary adapter — HTTP interface for contribution management.
 * Delegates all logic to input ports (use cases) and output ports (repositories).
 * No SQL in this class.
 */
@Tag(name = "Contributions", description = "Contribution cycles, manual payments, and STK Push")
@RestController
@RequestMapping("/api/v1/contributions")
@RequiredArgsConstructor
public class ContributionController {

    private final RecordContributionPort recordContributionUseCase;
    private final InitiateStkPushPort initiateStkPushUseCase;
    private final GetCycleSummaryPort getCycleSummaryUseCase;
    private final ContributionCycleManagementRepository cycleManagement;

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<ContributionEntryResponse>> recordManual(
            @Valid @RequestBody RecordManualPaymentRequest request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                recordContributionUseCase.execute(request, UUID.fromString(userId)),
                "Payment recorded successfully"));
    }

    @PostMapping("/stk-push")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER')")
    public ResponseEntity<ApiResponse<StkPushResponse>> initiateStkPush(
            @Valid @RequestBody InitiateStkPushRequest request,
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(
                initiateStkPushUseCase.execute(request, UUID.fromString(userId)),
                "Payment request sent. Please check your phone and enter your M-Pesa PIN."));
    }

    @GetMapping("/cycles/{cycleId}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','SECRETARY','AUDITOR')")
    public ResponseEntity<ApiResponse<CycleSummaryResponse>> getCycleSummary(
            @PathVariable UUID cycleId) {
        return ResponseEntity.ok(ApiResponse.success(getCycleSummaryUseCase.execute(cycleId)));
    }

    @GetMapping("/cycles")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<CycleSummaryRow>>> listCycles() {
        UUID groupId = TenantContext.getGroupId();
        return ResponseEntity.ok(ApiResponse.success(cycleManagement.findCyclesByGroup(groupId)));
    }

    @PutMapping("/cycles/{cycleId}/mgr-beneficiary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setMgrBeneficiary(
            @PathVariable UUID cycleId,
            @jakarta.validation.Valid @RequestBody SetMgrBeneficiaryRequest body,
            @AuthenticationPrincipal String userId) {

        UUID groupId = TenantContext.getGroupId();
        UUID memberId = body.memberId();
        BigDecimal payoutAmount = body.payoutAmount();

        cycleManagement.setMgrBeneficiary(cycleId, groupId, memberId, payoutAmount);
        String memberName = cycleManagement.findMemberFullName(memberId, groupId);

        return ResponseEntity.ok(ApiResponse.success(null,
                memberName + " set as MGR beneficiary" +
                (payoutAmount != null ? " — payout KES " + String.format("%,.0f", payoutAmount) : "")));
    }

    @PostMapping("/cycles/open")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<OpenCycleResult>> openCycle(
            @jakarta.validation.Valid @RequestBody OpenCycleRequest body) {

        UUID groupId = TenantContext.getGroupId();
        LocalDate dueDate = LocalDate.parse(body.dueDate());
        int graceDays = body.gracePeriodDays() != null ? body.gracePeriodDays() : 3;
        UUID mgrBeneficiary = body.mgrBeneficiaryMemberId();

        OpenCycleResult result = cycleManagement.openCycle(groupId, dueDate, graceDays, mgrBeneficiary);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result,
                "Cycle " + result.cycleNumber() + "/" + result.year() + " opened. " +
                result.memberCount() + " contribution entries created. " +
                "Total expected: KES " + String.format("%,.0f", result.totalExpected())));
    }

    // ── Typed request bodies (replaces raw Map<String,Object>) ───────────────
    public record SetMgrBeneficiaryRequest(
            @jakarta.validation.constraints.NotNull java.util.UUID memberId,
            java.math.BigDecimal payoutAmount
    ) {}

    public record OpenCycleRequest(
            @jakarta.validation.constraints.NotNull String dueDate,
            Integer gracePeriodDays,
            java.util.UUID mgrBeneficiaryMemberId
    ) {}

}
