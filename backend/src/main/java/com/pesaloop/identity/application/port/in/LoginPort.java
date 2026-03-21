package com.pesaloop.identity.application.port.in;

import com.pesaloop.identity.application.usecase.AuthUseCase.LoginResult;
import java.util.UUID;

/** Input port — authenticate a user and return group context. */
public interface LoginPort {
    LoginResult login(String rawPhone, String password, UUID groupId);
}
