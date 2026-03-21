package com.pesaloop.contribution.adapters.web;

import com.pesaloop.contribution.application.port.in.GetMonthlyLedgerPort;
import com.pesaloop.contribution.application.port.out.MemberStatementRepository;
import com.pesaloop.contribution.application.port.out.MemberStatementRepository.*;
import com.pesaloop.contribution.application.usecase.MonthlyLedgerUseCase.MonthlyLedgerResponse;
import com.pesaloop.shared.adapters.web.ApiResponse;
import com.pesaloop.shared.domain.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Primary adapter — reports for the treasurer.
 * No SQL in this class — all data access through output port interfaces.
 */
@Tag(name = "Reports", description = "Monthly ledger, member statements, loan book")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final GetMonthlyLedgerPort monthlyLedgerUseCase;
    private final MemberStatementRepository memberStatementRepository;

    @GetMapping("/monthly-ledger")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<MonthlyLedgerResponse>> monthlyLedger(
            @RequestParam(required = false) String month) {
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.now();
        return ResponseEntity.ok(ApiResponse.success(monthlyLedgerUseCase.execute(ym)));
    }

    @GetMapping("/member/{memberId}/statement")
    @PreAuthorize("hasAnyRole('ADMIN','TREASURER','AUDITOR')")
    public ResponseEntity<ApiResponse<MemberStatement>> memberStatement(
            @PathVariable UUID memberId) {
        UUID groupId = TenantContext.getGroupId();
        MemberProfile profile = memberStatementRepository.findMemberProfile(memberId, groupId);
        List<ContributionLine> contributions = memberStatementRepository.findContributions(memberId, groupId);
        List<LoanLine> loans = memberStatementRepository.findLoans(memberId, groupId);
        List<RepaymentLine> repayments = memberStatementRepository.findRepayments(memberId, groupId);
        return ResponseEntity.ok(ApiResponse.success(
                new MemberStatement(memberId, profile, contributions, loans, repayments)));
    }

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

    record MemberStatement(UUID memberId, MemberProfile profile,
                           List<ContributionLine> contributions,
                           List<LoanLine> loans,
                           List<RepaymentLine> repayments) {}
}
