package com.krath.utilisim.sentinel.smithy.user;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.vault.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserServiceImpl service;

    @Test
    @DisplayName("registerUser delegates to the repository")
    void registerDelegates() {
        AppUser user = AppUser.builder().email("ada@example.com").build();
        when(repository.save(user)).thenReturn(user);

        assertSame(user, service.registerUser(user));
        verify(repository).save(user);
    }

    @Test
    @DisplayName("findByEmail delegates to the repository")
    void findByEmailDelegates() {
        AppUser user = AppUser.builder().email("ada@example.com").build();
        when(repository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), service.findByEmail("ada@example.com"));
    }
}
