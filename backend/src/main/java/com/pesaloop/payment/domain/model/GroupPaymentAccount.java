package com.pesaloop.payment.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents one payment account belonging to a group.
 *
 * A group can have multiple accounts:
 *   - An M-Pesa Paybill for receiving contributions
 *   - An M-Pesa Till as an alternative collection channel
 *   - A bank account for large transfers / diaspora payments
 *   - A separate B2C shortcode for outbound disbursements
 *
 * The account_type tells you WHAT the account is.
 * The provider tells you WHO runs the rails.
 * These are orthogonal — Equity Bank is a provider, BANK_ACCOUNT is the type.
 */
public record GroupPaymentAccount(
        UUID id,
        UUID groupId,
        AccountType accountType,
        Provider provider,
        String accountNumber,
        String accountName,

        // Bank-specific fields (null for M-Pesa accounts)
        String bankBranch,
        String bankSwiftCode,
        String bankSortCode,

        // M-Pesa C2B registration status
        boolean c2bRegistered,
        Instant c2bRegisteredAt,

        // Purpose flags
        boolean isCollection,    // this account receives money IN
        boolean isDisbursement,  // this account sends money OUT
        boolean isPrimary,       // default account for its purpose

        AccountStatus status,
        String displayLabel,

        UUID createdBy,
        Instant createdAt
) {

    // ── Account type — WHAT kind of account ──────────────────────────────────

    public enum AccountType {
        MPESA_PAYBILL,    // Lipa na M-Pesa paybill — standard for most chamaas
        MPESA_TILL,       // Buy Goods till number — simpler but no account reference
        MPESA_B2C,        // Outbound disbursements (may reuse paybill shortcode)
        BANK_ACCOUNT,     // Regular bank savings/current account
        BANK_PAYBILL,     // Bank-issued paybill (Equity 247247, KCB 522522, etc.)
        PESALINK          // PesaLink instant interbank transfer
    }

    // ── Provider — WHO runs the payment rails ─────────────────────────────────

    public enum Provider {
        // Mobile money
        SAFARICOM_MPESA,
        AIRTEL_MONEY,
        TKASH,            // Telkom Kenya T-Kash

        // Banks (Kenya)
        EQUITY_BANK,
        KCB,
        COOPERATIVE_BANK,
        NCBA,
        ABSA,
        STANBIC,
        FAMILY_BANK,
        DTB,              // Diamond Trust Bank
        I_AND_M,
        SIDIAN_BANK,
        PESALINK,
        OTHER_BANK;

        public boolean isMobileMoney() {
            return this == SAFARICOM_MPESA || this == AIRTEL_MONEY || this == TKASH;
        }

        public boolean isBank() {
            return !isMobileMoney();
        }

        public String displayName() {
            return switch (this) {
                case SAFARICOM_MPESA  -> "Safaricom M-Pesa";
                case AIRTEL_MONEY    -> "Airtel Money";
                case TKASH           -> "Telkom T-Kash";
                case EQUITY_BANK     -> "Equity Bank";
                case KCB             -> "KCB Bank";
                case COOPERATIVE_BANK-> "Co-operative Bank";
                case NCBA            -> "NCBA Bank";
                case ABSA            -> "ABSA Bank";
                case STANBIC         -> "Stanbic Bank";
                case FAMILY_BANK     -> "Family Bank";
                case DTB             -> "Diamond Trust Bank";
                case I_AND_M         -> "I&M Bank";
                case SIDIAN_BANK     -> "Sidian Bank";
                case PESALINK        -> "PesaLink";
                case OTHER_BANK      -> "Other Bank";
            };
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public enum AccountStatus {
        ACTIVE,
        INACTIVE,
        PENDING_VERIFICATION,
        SUSPENDED
    }

    // ── Derived helpers ───────────────────────────────────────────────────────

    public boolean isMpesa() {
        return provider == Provider.SAFARICOM_MPESA;
    }

    public boolean requiresC2bRegistration() {
        return accountType == AccountType.MPESA_PAYBILL && isMpesa();
    }

    public boolean isReadyForCollection() {
        return status == AccountStatus.ACTIVE
                && isCollection
                && (!requiresC2bRegistration() || c2bRegistered);
    }

    /**
     * Human-readable description for display in UI and SMS messages.
     * Examples:
     *   "Safaricom M-Pesa Paybill 522533"
     *   "KCB Bank — Acc 0111234567890"
     *   "Safaricom M-Pesa Till 891234"
     */
    public String shortDescription() {
        return switch (accountType) {
            case MPESA_PAYBILL  -> "M-Pesa Paybill " + accountNumber;
            case MPESA_TILL     -> "M-Pesa Till " + accountNumber;
            case MPESA_B2C      -> "M-Pesa B2C " + accountNumber;
            case BANK_ACCOUNT   -> provider.displayName() + " — " + maskAccountNumber();
            case BANK_PAYBILL   -> provider.displayName() + " Paybill " + accountNumber;
            case PESALINK       -> "PesaLink — " + maskAccountNumber();
        };
    }

    private String maskAccountNumber() {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "••" + accountNumber.substring(accountNumber.length() - 4);
    }
}
