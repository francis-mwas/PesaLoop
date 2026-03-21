package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.dto.LoanDtos.ApplyForLoanRequest;
import com.pesaloop.loan.application.dto.LoanDtos.LoanApplicationResponse;
import java.util.UUID;

/** Input port — submit a loan application. */
public interface ApplyForLoanPort {
    LoanApplicationResponse execute(ApplyForLoanRequest request, UUID applicantUserId);
}
