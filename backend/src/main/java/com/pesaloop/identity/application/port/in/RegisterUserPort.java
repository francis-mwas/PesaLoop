package com.pesaloop.identity.application.port.in;

import com.pesaloop.identity.application.usecase.AuthUseCase.RegisterResult;

/** Input port — register a new user account. */
public interface RegisterUserPort {
    RegisterResult register(String phoneNumber, String fullName, String password, String email);
}
