package com.krath.utilisim.sentinel.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.foundation.scrolls.user.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the password-hash disclosure (SECURITY_REVIEW NEW-01).
 */
class UserControllerTest {

    private final UserController controller = new UserController();
    private final ObjectMapper mapper = new ObjectMapper();

    private AppUser user() {
        return AppUser.builder()
                .id("u1")
                .username("Ada")
                .email("ada@example.com")
                .password("$2a$10$hash-must-never-leave-the-server")
                .build();
    }

    @Test
    @DisplayName("GET /me returns the signed-in account as a DTO")
    void returnsCurrentUser() {
        UserResponse response = controller.me(user());

        assertEquals("u1", response.id());
        assertEquals("ada@example.com", response.email());
        // AppUser.getUsername() is the security identifier, i.e. the email.
        assertEquals("ada@example.com", response.username());
    }

    @Test
    @DisplayName("the user response never contains a password property")
    void responseHasNoPassword() throws Exception {
        String json = mapper.writeValueAsString(controller.me(user()));

        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("$2a$10$"), json);
    }

    @Test
    @DisplayName("an AppUser entity never serializes its password hash")
    void entityHidesPassword() throws Exception {
        String json = mapper.writeValueAsString(user());

        assertFalse(json.contains("password"), json);
        assertFalse(json.contains("$2a$10$"), json);
        assertTrue(json.contains("ada@example.com"));
    }

    @Test
    @DisplayName("projection rejects a null entity")
    void rejectsNullUser() {
        assertThrows(NullPointerException.class, () -> UserResponse.from(null));
    }
}
