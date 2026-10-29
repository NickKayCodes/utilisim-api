package com.krath.utilisim.sentinel.smithy.user;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.vault.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    @DisplayName("loadUserByUsername returns the stored user")
    void returnsStoredUser() {
        AppUser user = AppUser.builder().id("u1").email("ada@example.com").build();
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        assertEquals(user, service.loadUserByUsername("ada@example.com"));
    }

    @Test
    @DisplayName("loadUserByUsername throws for an unknown email")
    void rejectsUnknownUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing@example.com"));

        assertEquals("User not found", ex.getMessage());
    }
}
