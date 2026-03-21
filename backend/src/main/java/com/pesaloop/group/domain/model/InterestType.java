package com.pesaloop.group.domain.model;

/**
 * The mathematical model used to calculate interest on a loan.
 *
 * FLAT:
 *   Interest = principal × rate × term
 *   Calculated once on the original principal. Monthly repayment is constant.
 *   Simple to explain to members. Common in informal chamaas.
 *   E.g. KES 100,000 loan, 10% flat rate, 12 months:
 *        Interest = 100,000 × 10% = 10,000 (total, charged once)
 *        Monthly repayment = (100,000 + 10,000) / 12 = 9,166.67
 *
 * REDUCING_BALANCE:
 *   Interest calculated on the outstanding balance each period.
 *   Each repayment reduces the principal, so interest decreases over time.
 *   Fairer to the borrower. Standard in formal banking.
 *   E.g. same loan at 10% annual (0.833%/month):
 *        Month 1: interest = 100,000 × 0.833% = 833
 *        Month 2: interest = (100,000 - repaid_principal) × 0.833%
 *        ... and so on
 *
 * SIMPLE_INTEREST:
 *   I = P × R × T (time in years). No compounding.
 *   Used for short-term loans (< 1 year).
 */
public enum InterestType {
    FLAT,
    REDUCING_BALANCE,
    SIMPLE_INTEREST
}
