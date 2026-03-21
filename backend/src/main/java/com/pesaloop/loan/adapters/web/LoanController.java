package com.pesaloop.loan.adapters.web;

import com.pesaloop.loan.application.dto.LoanDtos.*;
import com.pesaloop.loan.application.port.in.*;
import com.pesaloop.loan.application.port.out.LoanAccountRepository;
import com.pesaloop.loan.application.port.out.LoanAccountRepository.LoanSummaryRow;
import com.pesaloop.loan.domain.model.LoanStatus;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Loans", description = "Loan lifecycle — apply, approve, disburse, repay")
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final ApplyForLoanPort      applyForLoanUseCase;
    private final ApproveLoanPort       approveLoanUseCase;
    private final DisburseLoanPort      disburseLoanUseCase;
    private final RecordRepaymentPort   recordRepaymentUseCase;
    private final LoanAccountRepository loanRepository;

    // ── Apply ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Submit a loan application",
               description = "Member applies for a loan. Eligibility is checked immediately against the product rules.")
    @PostMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> apply(
            @Valid @RequestBody ApplyForLoanRequest request,
            @AuthenticationPrincipal String userId) {

        LoanApplicationResponse response =
                applyForLoanUseCase.execute(request, UUID.fromString(userId));

        if (!Boolean.TRUE.equals(response.eligible()) && response.loanId() == null) {
            return ResponseEntity.unprocessableEntity()
                    .body(ApiResponse.error(response.ineligibilityReason(), "INELIGIBLE"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Loan application submitted successfully"));
    }

    // ── Pending applications ──────────────────────────────────────────────────

    @Operation(summary = "List pending loan applications",
               description = "Returns loans in PENDING_GUARANTOR or PENDING_APPROVAL status awaiting admin action.")
    @GetMapping("/applications/pending")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<List<LoanSummaryRow>>> getPendingApplications() {
        UUID groupId = TenantContext.getGroupId();
        List<LoanSummaryRow> loans = loanRepository.findLoanBook(
                groupId, List.of("PENDING_GUARANTOR", "PENDING_APPROVAL"));
        return ResponseEntity.ok(ApiResponse.success(loans));
    }

    // ── Approve / reject ──────────────────────────────────────────────────────

    @Operation(summary = "Approve or reject a loan application",
               description = "Admin approves (optionally with a reduced amount) or rejects a pending application.")
    @PutMapping("/applications/{loanId}/process")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<LoanDetailResponse>> processApplication(
            @PathVariable UUID loanId,
            @Valid @RequestBody ProcessLoanApplicationRequest request,
            @AuthenticationPrincipal String userId) {

        if (!loanId.equals(request.loanId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Loan ID in path does not match request body", "MISMATCH"));
        }

        // Map the web DTO → use case DTO
        ProcessLoanRequest approveRequest = new ProcessLoanRequest(
                request.loanId(),
                ProcessLoanRequest.LoanDecision.valueOf(request.decision().name()),
                request.note(),
                request.approvedAmount() != null ? request.approvedAmount().doubleValue() : null,
                null
        );

        LoanDetailResponse response = approveLoanUseCase.execute(approveRequest, UUID.fromString(userId));
        String message = request.decision() == ProcessLoanApplicationRequest.LoanDecision.APPROVE
                ? "Loan approved successfully"
                : "Loan application rejected";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    // ── Disburse ──────────────────────────────────────────────────────────────

    @Operation(summary = "Issue disbursement instruction for an approved loan")
    @PostMapping("/{loanId}/disburse")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER')")
    public ResponseEntity<ApiResponse<DisburseResponse>> disburse(
            @PathVariable UUID loanId,
            @RequestBody(required = false) DisburseLoanRequest request,
            @AuthenticationPrincipal String userId) {

        DisburseLoanRequest effectiveRequest = request != null
                ? request : new DisburseLoanRequest(loanId, null);

        DisburseResponse response = disburseLoanUseCase.execute(effectiveRequest, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response,
                "Disbursement initiated. Member will receive funds shortly."));
    }

    // ── Record repayment ──────────────────────────────────────────────────────

    @Operation(summary = "Record a manual loan repayment",
               description = "Records cash or bank transfer. M-Pesa STK Push repayments arrive via the webhook.")
    @PostMapping("/{loanId}/repayments")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','MEMBER')")
    public ResponseEntity<ApiResponse<RepaymentResponse>> recordRepayment(
            @PathVariable UUID loanId,
            @Valid @RequestBody RecordRepaymentRequest request,
            @AuthenticationPrincipal String userId) {

        if (!loanId.equals(request.loanId())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Loan ID mismatch", "MISMATCH"));
        }
        RepaymentResponse response = recordRepaymentUseCase.execute(request, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(response,
                response.loanStatus() == LoanStatus.SETTLED
                        ? "Loan fully settled!"
                        : "Repayment recorded. Outstanding: KES " + response.remainingOutstanding()));
    }

    // ── Get single loan ───────────────────────────────────────────────────────

    @Operation(summary = "Get full loan details with repayment schedule")
    @GetMapping("/{loanId}")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','SECRETARY','MEMBER','AUDITOR')")
    public ResponseEntity<ApiResponse<LoanDetailResponse>> getLoan(@PathVariable UUID loanId) {
        return loanRepository.findDetailWithInstallments(loanId)
                .map(d -> mapToDetailResponse(d))
                .map(detail -> ResponseEntity.ok(ApiResponse.success(detail)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ── Loan book ─────────────────────────────────────────────────────────────

    @Operation(summary = "Group loan book",
               description = "Lists all loans for the group. Filter by ?status=ACTIVE|DEFAULTED|PENDING_APPROVAL etc.")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<LoanSummaryRow>>> getLoanBook(
            @Parameter(description = "Loan status filter. Omit for all active/pending/defaulted loans.")
            @RequestParam(required = false) String status) {

        UUID groupId = TenantContext.getGroupId();
        List<String> statuses = status != null
                ? List.of(status.toUpperCase())
                : List.of("ACTIVE", "DEFAULTED", "PENDING_APPROVAL", "APPROVED", "PENDING_DISBURSEMENT");

        return ResponseEntity.ok(ApiResponse.success(loanRepository.findLoanBook(groupId, statuses)));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private LoanDetailResponse mapToDetailResponse(LoanAccountRepository.LoanDetailWithSchedule d) {
        var detail = d.detail();
        List<InstallmentResponse> schedule = d.schedule().stream()
                .map(i -> new InstallmentResponse(
                        i.installmentNumber(), i.dueDate(),
                        i.principalDue(), i.interestDue(), i.totalDue(),
                        null,
                        i.principalPaid(), i.interestPaid(), i.penaltyPaid(),
                        com.pesaloop.loan.domain.model.RepaymentInstallment.InstallmentStatus
                                .valueOf(i.status() != null ? i.status() : "PENDING"),
                        i.paidAt()))
                .toList();

        return new LoanDetailResponse(
                detail.id(), detail.loanReference(),
                detail.memberId(), detail.memberName(), detail.memberNumber(),
                detail.productName(), LoanStatus.valueOf(detail.status()),
                detail.principalAmount().getAmount(),
                detail.totalInterestCharged().getAmount(),
                detail.principalBalance().getAmount(),
                detail.accruedInterest().getAmount(),
                detail.penaltyBalance().getAmount(),
                detail.principalBalance().add(detail.accruedInterest()).add(detail.penaltyBalance()).getAmount(),
                detail.totalPrincipalRepaid().getAmount(),
                detail.totalInterestRepaid().getAmount(),
                detail.disbursementDate(), detail.dueDate(),
                detail.disbursementMpesaRef(), schedule);
    }
}
