package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.usecase.DisbursementService.DisbursementConfirmationResult;
import com.pesaloop.loan.application.usecase.DisbursementService.DisbursementInstruction;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Input port — manage disbursement instructions (generate, confirm, cancel). */
public interface DisbursementPort {
    UUID generateInstruction(UUID groupId, UUID loanId, String instructionType,
                             UUID recipientMemberId, String recipientName,
                             String recipientPhone, BigDecimal amountKes,
                             String sourceReference, UUID issuedByUserId);

    DisbursementConfirmationResult confirmDisbursement(UUID instructionId, String externalMpesaRef,
                                                       String confirmationNotes, UUID confirmedByUserId);

    void cancelInstruction(UUID instructionId, String reason, UUID cancelledByUserId);

    List<DisbursementInstruction> getPendingInstructions(UUID groupId);
}
