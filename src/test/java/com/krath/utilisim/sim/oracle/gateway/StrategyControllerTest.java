package com.krath.utilisim.sim.oracle.gateway;

import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.oracle.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyControllerTest {

    @Mock
    private StrategyService strategyService;

    @Mock
    private OracleService oracleService;

    private StrategyController controller;

    private final DebtSnapshot debt = new DebtSnapshot(
            "t1", "Visa", 1000f, 20f, 20f, 35f, 20f);

    @BeforeEach
    void setUp() {
        controller = new StrategyController();
        ReflectionTestUtils.setField(controller, "strategyService", strategyService);
        ReflectionTestUtils.setField(controller, "oracleService", oracleService);
    }

    @Test
    @DisplayName("orderDebts rejects a request without a strategy type")
    void rejectsMissingStrategy() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.orderDebts(new StrategyOrderRequest(List.of(debt), null)));
        assertEquals("input.strategyType is required", ex.getMessage());

        StrategyInput noType = new StrategyInput(null, null, 0f, 0f);
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.orderDebts(new StrategyOrderRequest(List.of(debt), noType)));
    }

    @Test
    @DisplayName("orderDebts defaults null debts to an empty list")
    void ordersWithNoDebts() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyOutput output = new StrategyOutput("SNOWBALL", List.of(), "none");
        when(strategyService.orderDebts(List.of(), input)).thenReturn(output);

        assertSame(output, controller.orderDebts(new StrategyOrderRequest(null, input)));
    }

    @Test
    @DisplayName("orderDebts delegates to the strategy service")
    void ordersDebts() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyOutput output = new StrategyOutput("SNOWBALL", List.of(debt), "smallest first");
        when(strategyService.orderDebts(List.of(debt), input)).thenReturn(output);

        assertSame(output, controller.orderDebts(new StrategyOrderRequest(List.of(debt), input)));
    }

    @Test
    @DisplayName("calculateTotalInterest delegates to the strategy service")
    void calculatesInterest() {
        when(strategyService.calculateTotalInterestForOrdering(List.of(debt))).thenReturn(123f);

        assertEquals(123f, controller.calculateTotalInterest(List.of(debt)));
    }

    @Test
    @DisplayName("isDeterministic parses known strategy names")
    void checksDeterminism() {
        when(strategyService.isOrderingDeterministic(StrategyType.AVALANCHE)).thenReturn(true);

        assertTrue(controller.isDeterministic("AVALANCHE"));
    }

    @Test
    @DisplayName("isDeterministic returns false for unknown strategy names")
    void rejectsUnknownStrategy() {
        assertFalse(controller.isDeterministic("NOT_A_STRATEGY"));
        verifyNoInteractions(strategyService);
    }

    @Test
    @DisplayName("recommendStrategy delegates to the oracle")
    void recommendsStrategy() {
        when(oracleService.recommendOptimalStrategy(List.of(debt))).thenReturn("AVALANCHE");

        assertEquals("AVALANCHE", controller.recommendStrategy(List.of(debt)));
    }

    @Test
    @DisplayName("analyzeStrategies rejects an empty strategy list")
    void rejectsEmptyStrategies() {
        SimulationInput input = new SimulationInput();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.analyzeStrategies(
                        new StrategyAnalysisRequest(List.of(debt), List.of(), input)));

        assertEquals("strategies must contain at least one strategy name", ex.getMessage());
    }

    @Test
    @DisplayName("analyzeStrategies rejects a missing input")
    void rejectsMissingInput() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.analyzeStrategies(
                        new StrategyAnalysisRequest(List.of(debt), List.of("SNOWBALL"), null)));

        assertEquals("input is required", ex.getMessage());
    }

    @Test
    @DisplayName("analyzeStrategies maps names to strategy inputs and delegates")
    void analyzesStrategies() {
        SimulationInput input = new SimulationInput();
        input.setExtraPayment(50f);
        input.setLumpSumPayment(25f);
        StrategyAnalysis analysis = new StrategyAnalysis(List.of(), "AVALANCHE");
        when(oracleService.analyzeStrategyEffectiveness(
                eq(List.of(debt)), anyList(), eq(input))).thenReturn(analysis);

        assertSame(analysis, controller.analyzeStrategies(
                new StrategyAnalysisRequest(List.of(debt), List.of("SNOWBALL", "AVALANCHE"), input)));

        verify(oracleService).analyzeStrategyEffectiveness(
                eq(List.of(debt)),
                argThat(strategies -> strategies.size() == 2
                        && strategies.get(0).strategyType() == StrategyType.SNOWBALL
                        && strategies.get(1).strategyType() == StrategyType.AVALANCHE
                        && strategies.get(1).extraPayment() == 50f
                        && strategies.get(1).lumpSumPayment() == 25f),
                eq(input));
    }

    @Test
    @DisplayName("analyzeStrategies defaults null debts to an empty list")
    void analyzesWithNoDebts() {
        SimulationInput input = new SimulationInput();
        StrategyAnalysis analysis = new StrategyAnalysis(List.of(), "MINIMUM_ONLY");
        when(oracleService.analyzeStrategyEffectiveness(eq(List.of()), anyList(), eq(input)))
                .thenReturn(analysis);

        assertSame(analysis, controller.analyzeStrategies(
                new StrategyAnalysisRequest(null, List.of("SNOWBALL"), input)));
    }
}
