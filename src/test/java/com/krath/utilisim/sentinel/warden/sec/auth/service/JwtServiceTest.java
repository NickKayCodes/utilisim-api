package com.krath.utilisim.sentinel.warden.sec.auth.service;

import com.krath.utilisim.sentinel.foundation.AppUser;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    /** Exactly 32 bytes: the shortest HS256 key the service accepts. */
    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtService jwtService = new JwtService(STRONG_SECRET);

    private AppUser user() {
        return AppUser.builder()
                .id("u1")
                .username("Ada")
                .email("ada@example.com")
                .password("hashed")
                .build();
    }

    @Test
    @DisplayName("a generated token carries the user's email and validates")
    void roundTripsToken() {
        AppUser user = user();

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertEquals("ada@example.com", jwtService.extractEmail(token));
        assertTrue(jwtService.isTokenValid(token, user));
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("a token is not valid for a different user")
    void rejectsTokenForAnotherUser() {
        String token = jwtService.generateToken(user());
        var other = User.withUsername("other@example.com").password("x").build();

        assertFalse(jwtService.isTokenValid(token, other));
    }

    @Test
    @DisplayName("a malformed token is rejected by the parser")
    void rejectsMalformedToken() {
        assertThrows(Exception.class, () -> jwtService.extractEmail("not-a-token"));
    }

    @Test
    @DisplayName("a configured secret keeps tokens valid across restarts")
    void reusesConfiguredSecret() {
        String token = jwtService.generateToken(user());

        JwtService afterRestart = new JwtService(STRONG_SECRET);

        assertEquals("ada@example.com", afterRestart.extractEmail(token));
        assertTrue(afterRestart.isTokenValid(token, user()));
    }

    @Test
    @DisplayName("a weak configured secret fails fast instead of signing with it")
    void rejectsWeakSecret() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtService("too-short"));

        assertTrue(error.getMessage().contains("32 bytes"));
    }

    @Test
    @DisplayName("without a configured secret each instance gets an ephemeral key")
    void ephemeralKeysDoNotOverlap() {
        JwtService first = new JwtService();
        JwtService second = new JwtService("   ");

        String token = first.generateToken(user());

        assertThrows(JwtException.class, () -> second.extractEmail(token));
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void rejectsForeignSignature() {
        JwtService other = new JwtService("fedcba9876543210fedcba9876543210");
        String token = other.generateToken(user());

        assertThrows(JwtException.class, () -> jwtService.extractEmail(token));
    }
}
