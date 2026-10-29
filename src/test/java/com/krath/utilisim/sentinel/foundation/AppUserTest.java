package com.krath.utilisim.sentinel.foundation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppUserTest {

    @Test
    @DisplayName("AppUser exposes the email as the security username")
    void usesEmailAsUsername() {
        AppUser user = AppUser.builder()
                .id("u1")
                .username("Ada")
                .email("ada@example.com")
                .password("hashed")
                .build();

        assertEquals("ada@example.com", user.getUsername());
        assertEquals("hashed", user.getPassword());
        assertTrue(user.getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("AppUser is always enabled and unexpired")
    void accountFlagsAreTrue() {
        AppUser user = new AppUser();

        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
        assertTrue(user.isEnabled());
    }
}
