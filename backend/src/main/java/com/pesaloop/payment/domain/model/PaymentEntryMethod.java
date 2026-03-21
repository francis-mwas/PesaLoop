package com.pesaloop.payment.domain.model;

/**
 * All supported ways a contribution or loan repayment can be received.
 *
 * Three primary entry points:
 *
 * 1. STK_PUSH
 *    System initiates. Member gets a PIN prompt on their phone.
 *    Triggered from PesaLoop UI by admin, treasurer, or the member themselves.
 *    Payment confirmed via /webhooks/mpesa/stk/callback.
 *
 * 2. C2B_PAYBILL
 *    Member initiates from their own phone.
 *    Steps: Go to M-Pesa → Lipa na M-Pesa → Pay Bill
 *           → Enter Business No. (group's paybill) → Enter Account No. (member number e.g. M-017)
 *           → Enter amount → Confirm with PIN
 *    Payment confirmed via /webhooks/mpesa/c2b/confirmation.
 *    This is extremely common — many members prefer to pay on their own without
 *    waiting for the treasurer to initiate a push.
 *
 * 3. MANUAL_CASH / MANUAL_BANK / MANUAL_CHEQUE
 *    Admin or treasurer records the payment manually.
 *    No M-Pesa involved. Used for cash collections at meetings.
 */
public enum PaymentEntryMethod {

    // ── M-Pesa initiated by system ────────────────────────────────────────────
    STK_PUSH,               // system pushed to member's phone

    // ── M-Pesa initiated by member (direct paybill) ───────────────────────────
    C2B_PAYBILL,            // member paid directly to group's paybill shortcode
    C2B_TILL,               // member paid to a till number

    // ── Manual entry by admin/treasurer ──────────────────────────────────────
    MANUAL_CASH,            // physical cash collected
    MANUAL_BANK_TRANSFER,   // member transferred via bank
    MANUAL_CHEQUE,          // cheque deposit
    MANUAL_OTHER            // any other method (noted in reference field)
}
