package com.krath.utilisim.sentinel.warden;

import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginResponse;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterResponse;
import com.krath.utilisim.sentinel.smithy.auth.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("login returns the service response with 200")
    void loginReturnsOk() {
        LoginRequest request = new LoginRequest("ada@example.com", "secret");
        LoginResponse expected = new LoginResponse("u1", "Ada", "ada@example.com", "token");
        when(authService.login(request)).thenReturn(expected);

        var response = controller.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    @DisplayName("register returns the service response with 200")
    void registerReturnsOk() {
        RegisterRequest request = new RegisterRequest("Ada", "secret", "ada@example.com");
        RegisterResponse expected = new RegisterResponse("u1", "Ada", "ada@example.com");
        when(authService.register(request)).thenReturn(expected);

        var response = controller.register(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
