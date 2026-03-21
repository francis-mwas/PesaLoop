package com.pesaloop.group.domain.model;

/**
 * How often interest accrues on outstanding loans.
 *
 * Real-world usage:
 * - FLAT_RATE:    Interest calculated once at loan issuance (most common in informal chamaas).
 *                 E.g. "borrow 10,000 → pay back 11,000" — the 1,000 is fixed at start.
 * - DAILY:        Common in micro-finance and bodaboda saccos
 * - WEEKLY:       Some urban chamaas
 * - FORTNIGHTLY:  Less common, but fully supported
 * - MONTHLY:      Standard for table-banking (e.g. 5% per month reducing balance)
 * - QUARTERLY:    For longer-term investment group loans
 * - ANNUALLY:     Annual simple interest on long-term loans
 * - CUSTOM:       Admin defines exact accrual interval in days
 *
 * The InterestType (FLAT vs REDUCING_BALANCE) is separate from accrual frequency —
 * they are orthogonal: you can have MONTHLY FLAT or MONTHLY REDUCING_BALANCE.
 */
public enum InterestAccrualFrequency {
    FLAT_RATE,          // one-time at disbursement; no periodic accrual
    DAILY,
    WEEKLY,
    FORTNIGHTLY,
    MONTHLY,
    QUARTERLY,
    ANNUALLY,
    CUSTOM              // uses customAccrualIntervalDays field on LoanProduct
}
