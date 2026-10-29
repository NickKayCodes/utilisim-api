package com.krath.utilisim.sim.forge.controller;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.forge.anvil.ForgeSimulationEngine;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.hammer.SimulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssembleControllerTest {

    @Mock
    private ForgeSimulationEngine engine;

    private AssembleController controller;

    @BeforeEach
    void setUp() {
        controller = new AssembleController();
        ReflectionTestUtils.setField(controller, "forgeSimulationEngine", engine);
    }

    @Test
    @DisplayName("runSimulation builds a scenario and delegates to the engine")
    void runsSimulation() {
        SimulationInput input = new SimulationInput();
        input.setPrincipal(1000f);
        input.setExtraPayment(50f);
        input.setLumpSumPayment(100f);
        input.setStrategy("SNOWBALL");
        SimulationResult expected = new SimulationResult();
        when(engine.run(eq(input), any(ScenarioContext.class))).thenReturn(expected);

        assertSame(expected, controller.runSimulation(input));

        ArgumentCaptor<ScenarioContext> captor = ArgumentCaptor.forClass(ScenarioContext.class);
        verify(engine).run(eq(input), captor.capture());
        ScenarioContext scenario = captor.getValue();
        assertEquals(50f, scenario.getExtraPayment());
        assertEquals(100f, scenario.getLumpSumPayment());
        assertEquals("SNOWBALL", scenario.getStrategy());
    }
}
