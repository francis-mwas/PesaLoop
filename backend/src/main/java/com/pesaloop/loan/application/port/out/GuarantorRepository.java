package com.pesaloop.loan.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — guarantor workflow persistence.
 * GuarantorUseCase depends on this; GuarantorJdbcAdapter implements it.
 */
public interface GuarantorRepository {

    void create(UUID loanId, UUID groupId, UUID guarantorMemberId);

    int respond(UUID loanId, UUID guarantorMemberId, boolean accepted, String note);

    int countAccepted(UUID loanId);

    List<GuarantorRecord> findByLoanId(UUID loanId, UUID groupId);

    // Loan context queries needed during guarantor workflow
    Optional<String> findLoanStatus(UUID loanId, UUID groupId);
    Optional<UUID>   findApplicantMemberId(UUID loanId);
    Optional<String> findLoanReference(UUID loanId);
    int              findRequiredGuarantorCount(UUID loanId);

    // Member/group lookups needed for SMS
    Optional<String> findMemberPhone(UUID memberId);
    Optional<String> findMemberFullName(UUID memberId);
    Optional<String> findGroupName(UUID groupId);
    Optional<String> findAdminPhone(UUID groupId);

    void advanceToApproval(UUID loanId);

    record GuarantorRecord(
            UUID id, UUID guarantorMemberId, String fullName,
            String memberNumber, String phone,
            String status, java.time.Instant respondedAt, String responseNote
    ) {}
}
