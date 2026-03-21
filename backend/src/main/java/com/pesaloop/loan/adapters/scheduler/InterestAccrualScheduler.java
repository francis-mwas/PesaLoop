package com.pesaloop.loan.adapters.scheduler;

import com.pesaloop.group.domain.model.InterestAccrualFrequency;
import com.pesaloop.group.domain.model.InterestType;
import com.pesaloop.loan.application.port.out.InterestAccrualRepository;
import com.pesaloop.loan.application.port.out.InterestAccrualRepository.LoanAccrualRow;
import com.pesaloop.loan.domain.service.InterestCalculationService;
import com.pesaloop.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Primary adapter — nightly interest accrual for all active non-flat-rate loans.
 * Runs at 1:00 AM daily (configurable via pesaloop.scheduler.interest-accrual-cron).
 *
 * For each active loan where today falls on an accrual boundary:
 *   1. Calculate interest using InterestCalculationService (pure domain logic)
 *   2. Persist via InterestAccrualRepository (output port)
 *
 * FLAT_RATE loans are skipped — interest is fixed at disbursement time.
 * No SQL in this class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterestAccrualScheduler {

    private final InterestAccrualRepository accrualRepository;
    private final InterestCalculationService interestService;

    @Scheduled(cron = "${pesaloop.scheduler.interest-accrual-cron:0 0 1 * * *}")
    public void accrueInterest() {
        LocalDate today = LocalDate.now();
        log.info("Interest accrual started: date={}", today);

        List<LoanAccrualRow> loans = accrualRepository.findActiveAccrualLoans();

        int processed = 0;
        int skipped = 0;
        int errors = 0;

        for (LoanAccrualRow loan : loans) {
            if (!isAccrualDay(today, loan)) { skipped++; continue; }

            try {
                processLoanAccrual(loan, today);
                processed++;
            } catch (Exception e) {
                log.error("Accrual failed: loan={} error={}", loan.loanId(), e.getMessage());
                errors++;
            }
        }

        log.info("Interest accrual complete: processed={} skipped={} errors={} date={}",
                processed, skipped, errors, today);
    }

    @Transactional
    protected void processLoanAccrual(LoanAccrualRow loan, LocalDate today) {
        Money principal  = Money.ofKes(loan.principalBalance());
        InterestAccrualFrequency frequency = InterestAccrualFrequency.valueOf(loan.accrualFrequency());
        BigDecimal annualRate = loan.interestRate();

        Money interest;
        if (InterestType.REDUCING_BALANCE.name().equals(loan.interestType())) {
            interest = interestService.calculateReducingBalanceInterestForPeriod(
                    principal, annualRate, frequency, loan.customAccrualDays());
        } else {
            // SIMPLE_INTEREST — daily accrual
            interest = interestService.calculateSimpleInterest(principal, annualRate, 1);
        }

        if (interest.isZero()) return;

        accrualRepository.recordAccruedInterest(
                loan.loanId(), loan.groupId(),
                interest.getAmount(), today, loan.accrualFrequency());

        log.debug("Accrued: loan={} amount={} frequency={}",
                loan.loanId(), interest.getAmount(), loan.accrualFrequency());
    }

    private boolean isAccrualDay(LocalDate today, LoanAccrualRow loan) {
        if (loan.disbursementDate() == null) return false;
        LocalDate disbursed = loan.disbursementDate();

        return switch (InterestAccrualFrequency.valueOf(loan.accrualFrequency())) {
            case DAILY       -> true;
            case WEEKLY      -> today.getDayOfWeek() == disbursed.getDayOfWeek();
            case FORTNIGHTLY -> {
                long days = ChronoUnit.DAYS.between(disbursed, today);
                yield days >= 0 && days % 14 == 0;
            }
            case MONTHLY     -> today.getDayOfMonth() == Math.min(
                    disbursed.getDayOfMonth(), today.lengthOfMonth());
            case QUARTERLY   -> today.getDayOfMonth() == disbursed.getDayOfMonth()
                    && (today.getMonthValue() - disbursed.getMonthValue()) % 3 == 0;
            case ANNUALLY    -> today.getDayOfMonth() == disbursed.getDayOfMonth()
                    && today.getMonth() == disbursed.getMonth();
            case CUSTOM      -> {
                int interval = loan.customAccrualDays() != null ? loan.customAccrualDays() : 30;
                long days = ChronoUnit.DAYS.between(disbursed, today);
                yield days >= 0 && days % interval == 0;
            }
            case FLAT_RATE   -> false;
        };
    }
}
