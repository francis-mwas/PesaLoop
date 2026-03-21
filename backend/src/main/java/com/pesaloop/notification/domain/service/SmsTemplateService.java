package com.pesaloop.notification.domain.service;

/**
 * Domain service — builds SMS messages from templates.
 *
 * Pure string logic — no I/O, no Spring dependencies.
 * Lives in domain/service because message templates are a business concern:
 * the phrasing, tone, and content of member communications are governed
 * by group policy (language, formality, detail level), not by infrastructure.
 *
 * Keeping templates here means they can be unit-tested without mocking
 * the SMS gateway, and changed without touching the gateway adapter.
 */
public class SmsTemplateService {

    public String otpMessage(int otp, String purpose) {
        return "PesaLoop: Your %s code is %d. Valid for 10 minutes. Do not share this code."
                .formatted(purpose.toLowerCase(), otp);
    }

    public String inviteMessage(String groupName, String inviteToken) {
        return ("You have been invited to join %s on PesaLoop. " +
                "Accept your invitation at pesaloop.app/invite/%s")
                .formatted(groupName, inviteToken);
    }

    public String contributionReminder(String memberNumber, String groupName,
                                        long amountKes, String dueDate) {
        return ("Hi %s, your %s contribution of KES %,d is due by %s. " +
                "Pay via M-Pesa using your member number as account reference.")
                .formatted(memberNumber, groupName, amountKes, dueDate);
    }

    public String paymentConfirmation(String memberNumber, long amountKes, long balanceRemaining) {
        String balanceMsg = balanceRemaining > 0
                ? " Balance remaining: KES %,d.".formatted(balanceRemaining)
                : " You are fully paid up!";
        return "PesaLoop: Payment of KES %,d received for %s.%s"
                .formatted(amountKes, memberNumber, balanceMsg);
    }

    public String loanApproved(String loanRef, long amountKes, String groupName) {
        return ("PesaLoop: Your loan %s for KES %,d has been approved by %s. " +
                "Awaiting disbursement — contact your treasurer.")
                .formatted(loanRef, amountKes, groupName);
    }

    public String loanDisbursed(String loanRef, long amountKes) {
        return "PesaLoop: KES %,d disbursed for loan %s. Please repay on time to avoid penalties."
                .formatted(amountKes, loanRef);
    }
}
