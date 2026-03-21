package com.pesaloop.notification.application.usecase;

import com.pesaloop.notification.application.port.in.SendNotificationPort;

import com.pesaloop.notification.application.port.out.SmsGateway;
import com.pesaloop.payment.application.port.out.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application service — orchestrates SMS sending via the SmsGateway port.
 * Never imports AfricasTalkingGateway or any concrete adapter.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService implements SendNotificationPort {

    private final SmsGateway smsGateway;   // port — never the concrete adapter
    private final PaymentRecordRepository paymentRecords;

    public void sendOtp(String phone, int otp, String purpose) {
        send(phone, buildOtpSms(otp, purpose), null, null);
    }

    public void sendInvite(String phone, String groupName, String inviteToken) {
        String link = "https://app.pesaloop.co.ke/invite/" + inviteToken;
        send(phone,
                String.format("You have been added to %s on PesaLoop. Set your password: %s", groupName, link),
                null, null);
    }

    public void sendContributionReminder(String phone, String memberNumber,
                                         String groupName, long amountKes,
                                         String dueDate, UUID groupId, UUID memberId) {
        send(phone,
                String.format("PesaLoop: %s, your contribution of KES %,d for %s is due %s.",
                        memberNumber, amountKes, groupName, dueDate),
                groupId, memberId);
    }

    public void sendPaymentConfirmation(String phone, String memberNumber,
                                        long amountKes, long balanceRemaining,
                                        String groupName, UUID groupId, UUID memberId) {
        String msg = balanceRemaining <= 0
                ? String.format("PesaLoop: KES %,d received for %s. Fully paid. Thank you!", amountKes, groupName)
                : String.format("PesaLoop: KES %,d received for %s. Remaining: KES %,d.", amountKes, groupName, balanceRemaining);
        send(phone, msg, groupId, memberId);
    }

    public void sendLoanApproved(String phone, String loanRef, long amountKes,
                                 String groupName, UUID groupId, UUID memberId) {
        send(phone,
                String.format("PesaLoop: Loan %s of KES %,d from %s approved. Treasurer will arrange disbursement.",
                        loanRef, amountKes, groupName),
                groupId, memberId);
    }

    public void sendLoanDisbursed(String phone, String loanRef, long amountKes,
                                  UUID groupId, UUID memberId) {
        send(phone,
                String.format("PesaLoop: Loan %s — KES %,d sent to your M-Pesa. Repayments start next month.",
                        loanRef, amountKes),
                groupId, memberId);
    }

    public void sendSubscriptionSms(String phone, String message, UUID groupId) {
        send(phone, message, groupId, null);
    }

    public void send(String phone, String message, UUID groupId, UUID memberId) {
        if (phone == null || phone.isBlank()) return;
        boolean sent = smsGateway.send(phone, message);
        // Only log when we have a group context — system SMS (OTP, invites) have no groupId
        if (groupId != null) {
            try {
                paymentRecords.logNotification(groupId, memberId, phone, message, sent);
            } catch (Exception e) {
                log.warn("Failed to log SMS: {}", e.getMessage());
            }
        }
    }

    private String buildOtpSms(int otp, String purpose) {
        return switch (purpose) {
            case "REGISTRATION" ->
                    String.format("PesaLoop: Your verification code is %d. Valid for 10 minutes. Do not share.", otp);
            case "LOGIN" ->
                    String.format("PesaLoop: Your login code is %d. Valid for 10 minutes.", otp);
            default ->
                    String.format("PesaLoop: Your code is %d. Valid for 10 minutes.", otp);
        };
    }
}