package com.pesaloop.notification.application.port.in;

import java.util.UUID;

/** Input port — send notifications to group members. */
public interface SendNotificationPort {
    void sendOtp(String phone, int otp, String purpose);
    void sendInvite(String phone, String groupName, String inviteToken);
    void sendContributionReminder(String phone, String memberNumber,
                                   String groupName, long amountKes,
                                   String dueDate, UUID groupId, UUID memberId);
    void sendPaymentConfirmation(String phone, String memberNumber,
                                  long amountKes, long balanceRemaining,
                                  String groupName, UUID groupId, UUID memberId);
    void sendLoanApproved(String phone, String loanRef, long amountKes,
                           String groupName, UUID groupId, UUID memberId);
    void sendLoanDisbursed(String phone, String loanRef, long amountKes,
                            UUID groupId, UUID memberId);
    void send(String phone, String message, UUID groupId, UUID memberId);
}
