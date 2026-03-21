package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.dto.LoanDtos.RecordRepaymentRequest;
import com.pesaloop.loan.application.dto.LoanDtos.RepaymentResponse;
import java.util.UUID;

/** Input port — record a loan repayment (cash, bank, or M-Pesa). */
public interface RecordRepaymentPort {
    RepaymentResponse execute(RecordRepaymentRequest request, UUID recordedByUserId);
}
