package com.krath.utilisim.archive.gate;

import com.krath.utilisim.archive.datastate.ScenarioRepository;
import com.krath.utilisim.archive.scenario.ScenarioContext;
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

@ExtendWith(MockitoExtension.class)
class ScenarioControllerTest {

    @Mock
    private ScenarioRepository repository;

    @InjectMocks
    private ScenarioController controller;

    private ScenarioContext scenario() {
        ScenarioContext scenario = new ScenarioContext();
        scenario.setId("s1");
        scenario.setName("Plan A");
        scenario.setStrategy("SNOWBALL");
        scenario.setExtraPayment(100f);
        scenario.setLumpSumPayment(50f);
        return scenario;
    }

    @Test
    @DisplayName("getAllScenarios returns every scenario")
    void listsScenarios() {
        List<ScenarioContext> scenarios = List.of(scenario());
        when(repository.findAll()).thenReturn(scenarios);

        assertEquals(scenarios, controller.getAllScenarios());
    }

    @Test
    @DisplayName("createScenario stamps timestamps and saves")
    void createsScenario() {
        ScenarioContext scenario = scenario();
        when(repository.save(any(ScenarioContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ScenarioContext created = controller.createScenario(scenario);

        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        verify(repository).save(scenario);
    }

    @Test
    @DisplayName("getScenarioById returns 200 when found")
    void findsScenario() {
        ScenarioContext scenario = scenario();
        when(repository.findById("s1")).thenReturn(Optional.of(scenario));

        var response = controller.getScenarioById("s1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(scenario, response.getBody());
    }

    @Test
    @DisplayName("getScenarioById returns 404 when missing")
    void missesScenario() {
        when(repository.findById("s1")).thenReturn(Optional.empty());

        var response = controller.getScenarioById("s1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("updateScenario copies editable fields and saves")
    void updatesScenario() {
        ScenarioContext existing = scenario();
        existing.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        ScenarioContext update = new ScenarioContext();
        update.setName("Plan B");
        update.setStrategy("AVALANCHE");
        update.setExtraPayment(200f);
        update.setLumpSumPayment(25f);

        when(repository.findById("s1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var response = controller.updateScenario("s1", update);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Plan B", response.getBody().getName());
        assertEquals("AVALANCHE", response.getBody().getStrategy());
        assertEquals(200f, response.getBody().getExtraPayment());
        assertEquals(25f, response.getBody().getLumpSumPayment());
        assertNotNull(response.getBody().getUpdatedAt());
        assertEquals(
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                response.getBody().getCreatedAt());
    }

    @Test
    @DisplayName("updateScenario returns 404 when missing")
    void updatesMissingScenario() {
        when(repository.findById("s1")).thenReturn(Optional.empty());

        var response = controller.updateScenario("s1", scenario());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deleteScenario removes an existing scenario")
    void deletesScenario() {
        when(repository.existsById("s1")).thenReturn(true);

        var response = controller.deleteScenario("s1");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(repository).deleteById("s1");
    }

    @Test
    @DisplayName("deleteScenario returns 404 when missing")
    void deletesMissingScenario() {
        when(repository.existsById("s1")).thenReturn(false);

        var response = controller.deleteScenario("s1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(repository, never()).deleteById("s1");
    }
}
