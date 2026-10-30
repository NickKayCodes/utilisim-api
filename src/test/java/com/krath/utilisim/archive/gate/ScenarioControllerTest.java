package com.krath.utilisim.archive.gate;

import com.krath.utilisim.archive.datastate.ScenarioRepository;
import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sentinel.foundation.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ownership regression tests for SECURITY_REVIEW API-08: scenarios must never be
 * readable, writable or deletable across accounts.
 */
@ExtendWith(MockitoExtension.class)
class ScenarioControllerTest {

    private static final AppUser OWNER = AppUser.builder().id("user-a").email("a@example.com").build();
    private static final AppUser OTHER = AppUser.builder().id("user-b").email("b@example.com").build();

    @Mock
    private ScenarioRepository repository;

    @InjectMocks
    private ScenarioController controller;

    private ScenarioContext scenario(String id, String userId) {
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId(id);
        scenario.setUserId(userId);
        scenario.setName("Plan A");
        scenario.setStrategy("SNOWBALL");
        scenario.setExtraPayment(100f);
        scenario.setLumpSumPayment(50f);
        return scenario;
    }

    @Test
    @DisplayName("GET /scenarios returns only the caller's scenarios")
    void listsOnlyOwnScenarios() {
        List<ScenarioContext> mine = List.of(scenario("s1", "user-a"));
        when(repository.findByUserId("user-a")).thenReturn(mine);

        assertSame(mine, controller.getAllScenarios(OWNER));
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("POST /scenarios forces the owner and ignores a spoofed userId and id")
    void createForcesOwner() {
        ScenarioContext request = scenario("someone-elses-id", "SPOOFED-USER-999");
        when(repository.save(any(ScenarioContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScenarioContext created = controller.createScenario(OWNER, request);

        assertEquals("user-a", created.getUserId());
        assertNull(created.getId(), "a client-supplied id must not select an existing row");
        verify(repository).save(request);
    }

    @Test
    @DisplayName("POST /scenarios stamps created and updated timestamps")
    void createStampsTimestamps() {
        ScenarioContext request = scenario(null, null);
        when(repository.save(any(ScenarioContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScenarioContext created = controller.createScenario(OWNER, request);

        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
    }

    @Test
    @DisplayName("GET /scenarios/{id} returns 200 for the owner")
    void findsOwnScenario() {
        ScenarioContext mine = scenario("s1", "user-a");
        when(repository.findByIdAndUserId("s1", "user-a")).thenReturn(Optional.of(mine));

        var response = controller.getScenarioById(OWNER, "s1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(mine, response.getBody());
    }

    @Test
    @DisplayName("GET /scenarios/{id} returns 404 for another user's scenario")
    void hidesForeignScenario() {
        when(repository.findByIdAndUserId("s1", "user-a")).thenReturn(Optional.empty());

        var response = controller.getScenarioById(OWNER, "s1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("PUT /scenarios/{id} copies editable fields and preserves ownership")
    void updatesOwnScenario() {
        ScenarioContext existing = scenario("s1", "user-a");
        existing.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        ScenarioContext update = scenario(null, "user-b");
        update.setName("Plan B");
        update.setStrategy("AVALANCHE");
        update.setExtraPayment(200f);
        update.setLumpSumPayment(25f);

        when(repository.findByIdAndUserId("s1", "user-a")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var response = controller.updateScenario(OWNER, "s1", update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Plan B", response.getBody().getName());
        assertEquals("AVALANCHE", response.getBody().getStrategy());
        assertEquals(200f, response.getBody().getExtraPayment());
        assertEquals(25f, response.getBody().getLumpSumPayment());
        assertEquals("user-a", response.getBody().getUserId());
        assertNotNull(response.getBody().getUpdatedAt());
        assertEquals(
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                response.getBody().getCreatedAt());
    }

    @Test
    @DisplayName("PUT /scenarios/{id} returns 404 and never saves another user's scenario")
    void cannotUpdateForeignScenario() {
        when(repository.findByIdAndUserId("s1", "user-a")).thenReturn(Optional.empty());

        var response = controller.updateScenario(OWNER, "s1", scenario(null, "user-a"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("DELETE /scenarios/{id} removes an owned scenario")
    void deletesOwnScenario() {
        when(repository.existsByIdAndUserId("s1", "user-a")).thenReturn(true);

        var response = controller.deleteScenario(OWNER, "s1");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(repository).deleteByIdAndUserId("s1", "user-a");
    }

    @Test
    @DisplayName("DELETE /scenarios/{id} returns 404 and never deletes another user's scenario")
    void cannotDeleteForeignScenario() {
        when(repository.existsByIdAndUserId("s1", "user-a")).thenReturn(false);

        var response = controller.deleteScenario(OWNER, "s1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(repository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("two accounts never see or mutate each other's scenarios")
    void isolatesTwoUsers() {
        ScenarioContext ownersScenario = scenario("s1", "user-a");
        when(repository.findByUserId("user-a")).thenReturn(List.of(ownersScenario));
        when(repository.findByUserId("user-b")).thenReturn(List.of());
        when(repository.findByIdAndUserId("s1", "user-b")).thenReturn(Optional.empty());
        when(repository.existsByIdAndUserId("s1", "user-b")).thenReturn(false);

        assertEquals(List.of(ownersScenario), controller.getAllScenarios(OWNER));
        assertEquals(List.of(), controller.getAllScenarios(OTHER));
        assertEquals(HttpStatus.NOT_FOUND, controller.getScenarioById(OTHER, "s1").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                controller.updateScenario(OTHER, "s1", scenario(null, "user-a")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.deleteScenario(OTHER, "s1").getStatusCode());
        verify(repository, never()).save(any());
        verify(repository, never()).deleteByIdAndUserId(any(), any());
    }
}
