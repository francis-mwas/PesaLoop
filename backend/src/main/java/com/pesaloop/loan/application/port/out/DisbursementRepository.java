package com.pesaloop.loan.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — disbursement instruction persistence.
 * DisbursementService depends on this, not on JdbcTemplate.
 */
public interface DisbursementRepository {

    /** Create a new PENDING disbursement instruction. */
    void create(UUID id, UUID groupId, String instructionType,
                UUID loanId, String sourceReference,
                UUID recipientMemberId, String recipientName, String recipientPhone,
                String suggestedAccountReference, BigDecimal amountKes,
                Instant expiresAt, UUID issuedByUserId);

    /** Find a pending instruction by ID and group. */
    Optional<DisbursementRecord> findPendingById(UUID id, UUID groupId);

    /** All pending, non-expired instructions for a group (treasurer queue). */
    List<DisbursementRecord> findPendingByGroup(UUID groupId);

    /** Count pending non-expired instructions for a group. */
    int countPendingByGroup(UUID groupId);

    /** Confirm: treasurer entered M-Pesa ref after manually sending. */
    void confirm(UUID id, UUID confirmedByUserId,
                 String externalMpesaRef, String notes);

    /** Cancel: reverts to CANCELLED, returns loan to APPROVED. */
    void cancel(UUID id, String reason, UUID cancelledByUserId);

    /** Check if a given M-Pesa ref has already confirmed a disbursement. */
    boolean mpesaRefAlreadyUsed(String externalMpesaRef);

    /** Get loan reference for a loan ID. */
    Optional<String> findLoanReference(UUID loanId);

    /** Get config value (e.g. disbursement_instruction_expiry_hours). */
    int getConfig(UUID groupId, String key, int defaultValue);

    record DisbursementRecord(
            UUID id,
            UUID groupId,
            String instructionType,
            UUID loanId,
            String sourceReference,
            UUID recipientMemberId,
            String recipientName,
            String recipientPhone,
            String suggestedAccountReference,
            BigDecimal amountKes,
            String status,
            Instant expiresAt,
            Instant issuedAt
    ) {}
}
