package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.dto.LoanDtos.DisburseLoanRequest;
import com.pesaloop.loan.application.dto.LoanDtos.DisburseResponse;
import java.util.UUID;

/** Input port — issue a disbursement instruction for an approved loan. */
public interface DisburseLoanPort {
    DisburseResponse execute(DisburseLoanRequest request, UUID disbursedByUserId);
}
