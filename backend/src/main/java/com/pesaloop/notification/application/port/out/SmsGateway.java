package com.pesaloop.notification.application.port.out;

import java.util.List;

/**
 * Output port — defines what the application needs from an SMS provider.
 * The domain declares this interface. The Africa's Talking adapter implements it.
 * SmsService depends on this — never on AfricasTalkingSmsClient directly.
 */
public interface SmsGateway {

    /**
     * Sends an SMS to a single phone number.
     *
     * @param phone   recipient in 254XXXXXXXXX format
     * @param message SMS body text
     * @return true if the provider accepted the message (delivery is async)
     */
    boolean send(String phone, String message);

    /**
     * Sends the same message to multiple recipients in one provider call.
     */
    boolean sendBulk(List<String> phones, String message);
}
