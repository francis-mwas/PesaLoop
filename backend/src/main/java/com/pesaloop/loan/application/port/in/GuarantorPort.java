package com.pesaloop.loan.application.port.in;

import com.pesaloop.loan.application.usecase.GuarantorUseCase.GuarantorResponse;
import com.pesaloop.loan.application.port.out.GuarantorRepository.GuarantorRecord;
import java.util.List;
import java.util.UUID;

/** Input port — guarantor nomination, acceptance, and querying. */
public interface GuarantorPort {
    void nominateGuarantors(UUID loanId, List<UUID> guarantorMemberIds, UUID nominatedByUserId);
    GuarantorResponse respond(UUID loanId, UUID guarantorMemberId, boolean accepted, String note);
    List<GuarantorRecord> getGuarantors(UUID loanId);
}
