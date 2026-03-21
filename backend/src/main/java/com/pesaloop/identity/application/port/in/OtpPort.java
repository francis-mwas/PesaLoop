package com.pesaloop.identity.application.port.in;

/** Input port — send and verify OTPs. */
public interface OtpPort {
    void sendOtp(String rawPhone, String purpose);
    void verifyOtp(String rawPhone, String code);
}
