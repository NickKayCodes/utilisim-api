package com.krath.utilisim.sim.forge.controller;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sim.forge.anvil.PlanComparisonService;
import com.krath.utilisim.sim.forge.hammer.ComparisonRequest;
import com.krath.utilisim.sim.forge.hammer.ComparisonResponse;
import com.krath.utilisim.sim.forge.hammer.PlanRunRequest;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulationControllerTest {

    @Mock
    private PlanComparisonService service;

    private SimulationController controller;
    private AppUser user;

    @BeforeEach
    void setUp() {
        controller = new SimulationController(service);
        user = AppUser.builder().id("user-1").email("ada@example.com").build();
    }

    @Test
    @DisplayName("compare delegates with the authenticated user")
    void compares() {
        ComparisonRequest request = new ComparisonRequest(
                List.of("t1"), null, null, null, null, null, null);
        ComparisonResponse expected = new ComparisonResponse(
                UUID.randomUUID(), null, 120, null, null, "basis", List.of());
        when(service.compare(request, "user-1")).thenReturn(expected);

        assertSame(expected, controller.compare(user, request));
    }

    @Test
    @DisplayName("run delegates with the authenticated user")
    void runs() {
        PlanRunRequest request = new PlanRunRequest(
                List.of("t1"), null, null, null, null, null, null);
        PlanTimeline expected = mock(PlanTimeline.class);
        when(service.run(request, "user-1")).thenReturn(expected);

        assertSame(expected, controller.run(user, request));
    }

    @Test
    @DisplayName("a persisted run is fetched by id")
    void getsRun() {
        PlanTimeline expected = mock(PlanTimeline.class);
        when(service.getRun(7L, "user-1")).thenReturn(expected);

        assertSame(expected, controller.run(user, 7L));
    }

    @Test
    @DisplayName("history passes the limit through")
    void listsHistory() {
        when(service.history("user-1", 5)).thenReturn(List.of());

        assertTrue(controller.history(user, 5).isEmpty());
    }

    @Test
    @DisplayName("a comparison is fetched by group id")
    void getsComparison() {
        UUID group = UUID.randomUUID();
        ComparisonResponse expected = new ComparisonResponse(
                group, null, 120, null, null, "basis", List.of());
        when(service.getComparison(group, "user-1")).thenReturn(expected);

        assertSame(expected, controller.comparison(user, group));
    }
}
