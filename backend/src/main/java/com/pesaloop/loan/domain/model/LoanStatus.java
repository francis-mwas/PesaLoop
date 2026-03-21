package com.pesaloop.loan.domain.model;

public enum LoanStatus {
    APPLICATION_SUBMITTED,  // member submitted application
    PENDING_GUARANTOR,      // waiting for guarantors to accept
    PENDING_APPROVAL,       // all guarantors confirmed, awaiting admin approval
    APPROVED,               // approved, not yet disbursed
    PENDING_DISBURSEMENT,   // disbursement instruction issued, awaiting treasurer confirmation
    DISBURSED,              // legacy alias for PENDING_DISBURSEMENT
    ACTIVE,                 // disbursement confirmed, repayments ongoing
    SETTLED,                // fully repaid
    DEFAULTED,              // passed due date with significant outstanding balance
    WRITTEN_OFF,            // deemed unrecoverable, removed from active book
    REJECTED,               // application rejected
    CANCELLED               // application cancelled by member before disbursement
}
