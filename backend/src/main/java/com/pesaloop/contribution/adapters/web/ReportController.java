package com.pesaloop.contribution.adapters.web;

import com.pesaloop.contribution.application.port.in.GetGroupStatsPort;
import com.pesaloop.contribution.application.port.in.GetGroupStatsPort.GroupStatsResult;
import com.pesaloop.contribution.application.port.in.GetMonthlyLedgerPort;
import com.pesaloop.contribution.application.port.in.GetYearSummaryPort;
import com.pesaloop.contribution.application.port.in.GetYearSummaryPort.YearSummaryResult;
import com.pesaloop.contribution.application.port.out.MemberStatementRepository;
import com.pesaloop.group.application.port.out.MemberQueryRepository;
import com.pesaloop.contribution.application.port.out.MemberStatementRepository.*;
import com.pesaloop.contribution.application.usecase.MonthlyLedgerUseCase.MonthlyLedgerResponse;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Primary adapter — HTTP interface for financial reports.
 * No SQL, no JdbcTemplate, no business logic.
 * Delegates everything to input ports (use cases) and output ports (repositories).
 */
@Tag(name = "Reports", description = "Monthly ledger, member statements, loan book, group earnings")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final GetMonthlyLedgerPort    monthlyLedgerUseCase;
    private final GetGroupStatsPort       groupStatsUseCase;
    private final GetYearSummaryPort      yearSummaryUseCase;
    private final MemberStatementRepository memberStatementRepository;
    private final MemberQueryRepository memberQueryRepository;

    // ── Monthly ledger ────────────────────────────────────────────────────

    @GetMapping("/monthly-ledger")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<MonthlyLedgerResponse>> monthlyLedger(
            @RequestParam(required = false) String month) {
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        return ResponseEntity.ok(ApiResponse.success(monthlyLedgerUseCase.execute(ym)));
    }

    // ── Member statement ──────────────────────────────────────────────────

    @GetMapping("/member/{memberId}/statement")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR','MEMBER')")
    public ResponseEntity<ApiResponse<MemberStatement>> memberStatement(
            @PathVariable UUID memberId,
            @AuthenticationPrincipal String userId) {
        UUID groupId = TenantContext.getGroupId();
        // MEMBER role can only view their own statement
        if ("MEMBER".equals(TenantContext.getRole())) {
            UUID callerMemberId = memberQueryRepository.findMemberIdByUserId(
                    UUID.fromString(userId), groupId).orElse(null);
            if (!memberId.equals(callerMemberId)) {
                return ResponseEntity.status(403)
                        .body(ApiResponse.error("You can only view your own statement", "FORBIDDEN"));
            }
        }
        MemberProfile        profile       = memberStatementRepository.findMemberProfile(memberId, groupId);
        List<ContributionLine> contributions = memberStatementRepository.findContributions(memberId, groupId);
        List<LoanLine>         loans         = memberStatementRepository.findLoans(memberId, groupId);
        List<RepaymentLine>    repayments    = memberStatementRepository.findRepayments(memberId, groupId);
        return ResponseEntity.ok(ApiResponse.success(
                new MemberStatement(memberId, profile, contributions, loans, repayments)));
    }

    // ── Loan book ─────────────────────────────────────────────────────────

    @GetMapping("/loan-book")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<List<LoanBookEntry>>> loanBook() {
        UUID groupId = TenantContext.getGroupId();
        List<LoanBookEntry> book = memberStatementRepository.findActiveLoanBook(groupId);
        BigDecimal total = book.stream()
                .map(LoanBookEntry::totalOutstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(ApiResponse.success(book,
                book.size() + " active loans · Total outstanding: KES " +
                        String.format("%,.0f", total)));
    }

    // ── Group stats (contributions banner) ───────────────────────────────

    @GetMapping("/group-stats")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR','MEMBER')")
    public ResponseEntity<ApiResponse<GroupStatsResult>> groupStats() {
        UUID groupId = TenantContext.getGroupId();
        int  year    = LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                groupStatsUseCase.execute(groupId, year)));
    }

    // ── Year-end earnings summary (dashboard) ─────────────────────────────

    @GetMapping("/year-summary")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR','MEMBER')")
    public ResponseEntity<ApiResponse<YearSummaryResult>> yearSummary(
            @RequestParam(required = false) Integer year) {
        UUID groupId   = TenantContext.getGroupId();
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success(
                yearSummaryUseCase.execute(groupId, targetYear)));
    }

    // ── Response wrapper records (controller-layer DTOs) ─────────────────

    record MemberStatement(
            UUID memberId,
            MemberProfile profile,
            List<ContributionLine> contributions,
            List<LoanLine> loans,
            List<RepaymentLine> repayments
    ) {}
}