package com.pesaloop.payment.application.port.in;

import com.pesaloop.payment.application.usecase.RecordManualPaymentUseCase.ManualPaymentRequest;
import com.pesaloop.payment.application.usecase.RecordManualPaymentUseCase.ManualPaymentResponse;
import java.util.UUID;

/** Input port — record a manual (cash/bank) payment. */
public interface RecordManualPaymentPort {
    ManualPaymentResponse execute(ManualPaymentRequest req, UUID recordedByUserId);
}
