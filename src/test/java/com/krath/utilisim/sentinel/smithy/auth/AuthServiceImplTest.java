package com.krath.utilisim.sentinel.smithy.auth;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.login.LoginResponse;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterRequest;
import com.krath.utilisim.sentinel.foundation.scrolls.register.RegisterResponse;
import com.krath.utilisim.sentinel.vault.UserRepository;
import com.krath.utilisim.sentinel.warden.sec.auth.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private AppUser user() {
        return AppUser.builder()
                .id("user-1")
                .username("Ada")
                .email("ada@example.com")
                .password("hashed")
                .build();
    }

    @Test
    @DisplayName("login authenticates and returns a token")
    void loginReturnsToken() {
        AppUser user = user();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("ada@example.com", "secret"));

        assertEquals("user-1", response.id());
        // Known behavior: AppUser.getUsername() returns the email, not the username field.
        assertEquals("ada@example.com", response.username());
        assertEquals("ada@example.com", response.email());
        assertEquals("jwt-token", response.token());
        verify(authenticationManager).authenticate(
                argThat(token -> "ada@example.com".equals(token.getPrincipal())
                        && "secret".equals(token.getCredentials())));
    }

    @Test
    @DisplayName("login turns any authentication failure into a generic message")
    void loginRejectsBadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("nope"));

        BadCredentialsException ex = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "wrong")));

        assertEquals("Invalid email or password", ex.getMessage());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("login reports a missing user after successful authentication")
    void loginRejectsMissingUser() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "secret")));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    @DisplayName("register hashes the password and saves the user")
    void registerSavesUser() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain")).thenReturn("hashed");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            saved.setId("user-2");
            return saved;
        });

        RegisterResponse response = authService.register(
                new RegisterRequest("Grace", "plain", "new@example.com"));

        assertEquals("user-2", response.id());
        // Known behavior: AppUser.getUsername() returns the email, not the username field.
        assertEquals("new@example.com", response.username());
        assertEquals("new@example.com", response.email());

        verify(userRepository).save(argThat(saved ->
                "Grace".equals(ReflectionTestUtils.getField(saved, "username"))
                        && "new@example.com".equals(saved.getEmail())
                        && "hashed".equals(saved.getPassword())));
    }

    @Test
    @DisplayName("register rejects an email that already exists")
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(
                        new RegisterRequest("Ada", "plain", "taken@example.com")));

        assertEquals("Email already in use", ex.getMessage());
        verify(userRepository, never()).save(any(AppUser.class));
    }
}
