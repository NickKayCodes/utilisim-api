package com.krath.utilisim.sentinel.smithy.auth;

import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginResponse;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    RegisterResponse register(RegisterRequest registerRequest);
}
