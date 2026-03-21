package com.pesaloop.contribution.application.port.in;

import com.pesaloop.contribution.application.usecase.MonthlyLedgerUseCase.MonthlyLedgerResponse;
import java.time.YearMonth;

/** Input port — get the monthly group ledger (the spreadsheet view). */
public interface GetMonthlyLedgerPort {
    MonthlyLedgerResponse execute(YearMonth month);
}
