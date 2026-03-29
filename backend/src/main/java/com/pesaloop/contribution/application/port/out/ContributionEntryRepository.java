package com.pesaloop.contribution.application.port.out;

import com.pesaloop.contribution.domain.model.ContributionEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionEntryRepository {
    ContributionEntry save(ContributionEntry entry);

    /**
     * Returns true if the reference already exists in payment_records for this group
     * with a matching payment method channel. False for CASH and blank references.
     */
    boolean isDuplicateReference(UUID groupId, String reference, String paymentMethod);
    Optional<ContributionEntry> findById(UUID id);
    Optional<ContributionEntry> findByCycleIdAndMemberId(UUID cycleId, UUID memberId);
    List<ContributionEntry> findByCycleId(UUID cycleId);
    List<ContributionEntry> findByMemberId(UUID memberId);

    /** All entries for a member across a financial year — used for dividend calc */
    List<ContributionEntry> findByMemberIdAndYear(UUID memberId, int year);

    /** Members who have not fully paid in a cycle — for reminders */
    List<ContributionEntry> findUnpaidByCycleId(UUID cycleId);

    /** Individual payment records for a specific member's entry in a cycle */
    List<EntryPayment> findPaymentsByEntryMember(UUID cycleId, UUID memberId, UUID groupId);

    record EntryPayment(
            java.math.BigDecimal amount, String paymentMethod,
            String mpesaReference, String narration,
            java.time.Instant recordedAt
    ) {}
}