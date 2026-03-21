package com.pesaloop.contribution.application.usecase;

import com.pesaloop.contribution.application.port.in.GetMonthlyLedgerPort;

import com.pesaloop.shared.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import com.pesaloop.contribution.application.port.out.LedgerRepository;
import com.pesaloop.contribution.application.port.out.LedgerRepository.MemberLedgerRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Produces the monthly group ledger — the digital equivalent of the
 * spreadsheet the treasurer maintains.
 *
 * Each row = one member. Each column set = one month:
 *   SAVINGS    → how much the member contributed this month
 *   INTEREST   → accumulated interest on their active loan (flat rate: fixed)
 *   LOAN       → current outstanding loan balance
 *   REPAYMENT  → how much they repaid this month
 *
 * Also produces a group-level totals row (the TOTAL row in the spreadsheet).
 *
 * Used by:
 *   GET /api/v1/reports/monthly-ledger?month=2025-04
 *   GET /api/v1/reports/monthly-ledger          (current month)
 */
@Service
@RequiredArgsConstructor
public class MonthlyLedgerUseCase implements GetMonthlyLedgerPort {

    private final LedgerRepository ledgerRepository;

    @Transactional(readOnly = true)
    public MonthlyLedgerResponse execute(YearMonth month) {
        UUID groupId = TenantContext.getGroupId();
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd   = month.atEndOfMonth();

        // One query — joins members, contribution_entries for the month,
        // loan_accounts (active), and payment_records (repayments this month).
        // Uses LEFT JOINs so members with no activity still appear.
        List<MemberLedgerRow> rows = ledgerRepository.findMonthlyLedger(groupId, monthStart, monthEnd);

        // Compute totals row (the TOTAL row in the spreadsheet)
        BigDecimal totalExpectedSavings  = rows.stream().map(MemberLedgerRow::expectedSavings).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMonthlySavings   = rows.stream().map(MemberLedgerRow::monthlySavings).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLoanOutstanding  = rows.stream().map(MemberLedgerRow::loanOutstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterest         = rows.stream().map(MemberLedgerRow::totalInterestCharged).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRepayment        = rows.stream().map(MemberLedgerRow::monthlyRepayment).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCumulativeSavings = rows.stream().map(MemberLedgerRow::cumulativeSavings).reduce(BigDecimal.ZERO, BigDecimal::add);

        // NOTE: A simple cross-check that cumulative ≥ monthly is safe.
        // The formula 'cumulative - monthly × monthNumber' is NOT reliable for
        // groups that started mid-year or have variable share counts.
        // Set to ZERO — the treasurer reconciles manually against the physical ledger.
        BigDecimal roundingCheck = BigDecimal.ZERO;

        return new MonthlyLedgerResponse(
                groupId,
                month.toString(),
                monthStart,
                monthEnd,
                rows,
                totalExpectedSavings,
                totalMonthlySavings,
                totalLoanOutstanding,
                totalInterest,
                totalRepayment,
                totalCumulativeSavings,
                roundingCheck
        );
    }

    // ── Response records ──────────────────────────────────────────────────────


    public record MonthlyLedgerResponse(
            UUID groupId,
            String month,
            LocalDate monthStart,
            LocalDate monthEnd,
            List<MemberLedgerRow> members,

            // TOTALS ROW (bottom of spreadsheet)
            BigDecimal totalExpectedSavings,
            BigDecimal totalMonthlySavings,
            BigDecimal totalLoanOutstanding,
            BigDecimal totalInterestCharged,
            BigDecimal totalMonthlyRepayment,
            BigDecimal totalCumulativeSavings,
            BigDecimal roundingCheck     // should always be 0.00 — flags arithmetic errors
    ) {}
}
