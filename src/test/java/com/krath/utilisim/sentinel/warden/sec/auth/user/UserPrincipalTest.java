package com.krath.utilisim.sentinel.warden.sec.auth.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserPrincipalTest {

    @Test
    @DisplayName("UserPrincipal exposes the id it was created with")
    void exposesId() {
        assertEquals("user-1", new UserPrincipal("user-1").getId());
    }
}
