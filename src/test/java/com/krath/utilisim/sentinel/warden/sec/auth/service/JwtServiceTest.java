package com.krath.utilisim.sentinel.warden.sec.auth.service;

import com.krath.utilisim.sentinel.foundation.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

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
}
