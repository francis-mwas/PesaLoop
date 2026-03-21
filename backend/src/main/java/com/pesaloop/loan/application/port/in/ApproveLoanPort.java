package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.dto.LoanDtos.LoanDetailResponse;
import com.pesaloop.loan.application.dto.LoanDtos.ProcessLoanRequest;
import java.util.UUID;

/** Input port — approve or reject a pending loan application. */
public interface ApproveLoanPort {
    LoanDetailResponse execute(ProcessLoanRequest request, UUID approvedByUserId);
}
