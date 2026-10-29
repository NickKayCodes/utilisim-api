package com.krath.utilisim.ledger.gateway;

import com.krath.utilisim.ledger.leaf.create.TomeRequest;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.ledger.vault.TomeService;
import com.krath.utilisim.sentinel.foundation.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TomeControllerTest {

    @Mock
    private TomeService tomeService;

    private TomeController controller;
    private AppUser user;

    @BeforeEach
    void setUp() {
        controller = new TomeController();
        ReflectionTestUtils.setField(controller, "tomeService", tomeService);
        user = AppUser.builder().id("user-1").email("ada@example.com").build();
    }

    private Tome tome() {
        Tome tome = new Tome();
        tome.setId("t1");
        tome.setUserId("user-1");
        tome.setName("Visa");
        tome.setType("CREDIT_CARD");
        return tome;
    }

    @Test
    @DisplayName("createTome saves for the authenticated user")
    void createsTome() {
        TomeRequest request = new TomeRequest("Visa", 1200f, 22f, 35f, 3000f, "CREDIT_CARD");
        when(tomeService.createTome(request, "user-1")).thenReturn(tome());

        var response = controller.createTome(user, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("t1", response.getBody().id());
        assertEquals("user-1", response.getBody().userId());
    }

    @Test
    @DisplayName("getMyTomes lists only the authenticated user's accounts")
    void listsTomes() {
        when(tomeService.getAllTomesByUserId("user-1")).thenReturn(List.of(tome()));

        var response = controller.getMyTomes(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Visa", response.getBody().get(0).name());
    }

    @Test
    @DisplayName("deleteTome delegates with the authenticated user id")
    void deletesTome() {
        var response = controller.deleteTome(user, "t1");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(tomeService).deleteTomeById("t1", "user-1");
    }
}
