package com.krath.utilisim.sentinel.gateway;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.smithy.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    @Test
    @DisplayName("findUserByEmail looks up the supplied email")
    void findsByEmail() {
        AppUser user = AppUser.builder().id("u1").email("ada@example.com").build();
        AppUser request = AppUser.builder().email("ada@example.com").build();
        when(userService.findByEmail("ada@example.com")).thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), controller.findUserByEmail(request));
    }
}
