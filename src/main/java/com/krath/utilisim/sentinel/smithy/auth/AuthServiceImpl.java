package com.krath.utilisim.sentinel.smithy.auth;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginResponse;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterResponse;
import com.krath.utilisim.sentinel.vault.UserRepository;
import com.krath.utilisim.sentinel.warden.sec.auth.service.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }


    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUser appUser = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String token = jwtService.generateToken(appUser);

        return new LoginResponse(
                appUser.getId(),
                appUser.getUsername(),
                appUser.getEmail(),
                token
        );
    }

    @Override
    public RegisterResponse register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        AppUser user = AppUser.builder()
                .username(registerRequest.username())
                .email(registerRequest.email())
                .password(passwordEncoder.encode(registerRequest.password()))
                .build();

        AppUser saved = userRepository.save(user);

        return new RegisterResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail()
        );
    }
}
