package com.krath.utilisim.sentinel.warden;

import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginResponse;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterResponse;
import com.krath.utilisim.sentinel.smithy.auth.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
}
