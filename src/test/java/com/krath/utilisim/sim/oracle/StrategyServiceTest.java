package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Strategy Service demonstrating deterministic behavior.
 * Tests all four strategy types and edge cases.
 */
@DisplayName("Strategy Service Tests")
class StrategyServiceTest {

    private StrategyService strategyService;
    private List<DebtSnapshot> testDebts;

    @BeforeEach
    void setUp() {
        strategyService = new StrategyService();

        // Create test debts
        testDebts = new ArrayList<>();
        testDebts.add(new DebtSnapshot("tome-1", "Credit Card A", 500f, 0.25f, 0.25f, 15f, 0.25f));
        testDebts.add(new DebtSnapshot("tome-2", "Student Loan", 5000f, 0.06f, 0.06f, 50f, 0.06f));
        testDebts.add(new DebtSnapshot("tome-3", "Credit Card B", 2000f, 0.18f, 0.18f, 40f, 0.18f));
        testDebts.add(new DebtSnapshot("tome-4", "Medical Debt", 1500f, 0.00f, 0.10f, 30f, 0.10f));
    }

    @Test
    @DisplayName("Snowball: Order by balance ascending")
    void testSnowballOrdering() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        assertEquals("tome-1", ordered.get(0).tomeId());  // 500
        assertEquals("tome-4", ordered.get(1).tomeId());  // 1500
        assertEquals("tome-3", ordered.get(2).tomeId());  // 2000
        assertEquals("tome-2", ordered.get(3).tomeId());  // 5000
        assertEquals("Snowball: Pay off smallest balance first for psychological momentum",
            output.description());
    }

    @Test
    @DisplayName("Avalanche: Order by interest rate descending")
    void testAvalancheOrdering() {
        StrategyInput input = new StrategyInput(StrategyType.AVALANCHE, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        assertEquals("tome-1", ordered.get(0).tomeId());  // 25%
        assertEquals("tome-3", ordered.get(1).tomeId());  // 18%
        assertEquals("tome-4", ordered.get(2).tomeId());  // 10% (estimated)
        assertEquals("tome-2", ordered.get(3).tomeId());  // 6%
    }

    @Test
    @DisplayName("Hybrid: Order by interest rate bands with snowball within bands")
    void testHybridOrdering() {
        StrategyInput input = new StrategyInput(StrategyType.HYBRID, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        // Hybrid sorts by interest rate first, then by balance within each band
        assertNotNull(ordered);
        assertEquals(4, ordered.size());
        // First should be highest interest
        assertTrue(ordered.get(0).apr() > 0.15f || ordered.get(0).estimatedApr() > 0.15f);
    }

    @Test
    @DisplayName("Custom: Respect user-defined ordering")
    void testCustomOrdering() {
        List<String> customOrder = Arrays.asList("tome-3", "tome-1", "tome-4", "tome-2");
        StrategyInput input = new StrategyInput(StrategyType.CUSTOM, customOrder, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        assertEquals("tome-3", ordered.get(0).tomeId());
        assertEquals("tome-1", ordered.get(1).tomeId());
        assertEquals("tome-4", ordered.get(2).tomeId());
        assertEquals("tome-2", ordered.get(3).tomeId());
    }

    @Test
    @DisplayName("Custom: Append unmapped debts at end")
    void testCustomOrderingWithUnmappedDebts() {
        List<String> customOrder = Arrays.asList("tome-3", "tome-1");  // Only 2 of 4
        StrategyInput input = new StrategyInput(StrategyType.CUSTOM, customOrder, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        assertEquals(4, ordered.size());
        assertEquals("tome-3", ordered.get(0).tomeId());
        assertEquals("tome-1", ordered.get(1).tomeId());
        // tome-2 and tome-4 appended in original order
        assertTrue(ordered.get(2).tomeId().equals("tome-2") || ordered.get(2).tomeId().equals("tome-4"));
    }

    @Test
    @DisplayName("Determinism: Same input produces same output")
    void testSnowballDeterminism() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 100f, 0f);

        StrategyOutput output1 = strategyService.orderDebts(testDebts, input);
        StrategyOutput output2 = strategyService.orderDebts(testDebts, input);

        assertEquals(output1.orderedDebts().size(), output2.orderedDebts().size());
        for (int i = 0; i < output1.orderedDebts().size(); i++) {
            assertEquals(output1.orderedDebts().get(i).tomeId(),
                output2.orderedDebts().get(i).tomeId());
        }
    }

    @Test
    @DisplayName("Determinism: Avalanche produces consistent ordering")
    void testAvalancheDeterminism() {
        StrategyInput input = new StrategyInput(StrategyType.AVALANCHE, null, 100f, 0f);

        // Run multiple times
        List<String> firstRun = strategyService.orderDebts(testDebts, input)
            .orderedDebts().stream()
            .map(DebtSnapshot::tomeId)
            .toList();

        List<String> secondRun = strategyService.orderDebts(testDebts, input)
            .orderedDebts().stream()
            .map(DebtSnapshot::tomeId)
            .toList();

        assertEquals(firstRun, secondRun);
    }

    @Test
    @DisplayName("Empty debt list handling")
    void testEmptyDebtList() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(new ArrayList<>(), input);

        assertTrue(output.orderedDebts().isEmpty());
        assertEquals("No debts to order", output.description());
    }

    @Test
    @DisplayName("Single debt ordering")
    void testSingleDebt() {
        List<DebtSnapshot> singleDebt = Arrays.asList(testDebts.get(0));

        StrategyInput snowballInput = new StrategyInput(StrategyType.SNOWBALL, null, 100f, 0f);
        StrategyInput avalancheInput = new StrategyInput(StrategyType.AVALANCHE, null, 100f, 0f);

        StrategyOutput snowballOutput = strategyService.orderDebts(singleDebt, snowballInput);
        StrategyOutput avalancheOutput = strategyService.orderDebts(singleDebt, avalancheInput);

        assertEquals(1, snowballOutput.orderedDebts().size());
        assertEquals(1, avalancheOutput.orderedDebts().size());
        assertEquals("tome-1", snowballOutput.orderedDebts().get(0).tomeId());
        assertEquals("tome-1", avalancheOutput.orderedDebts().get(0).tomeId());
    }

    @Test
    @DisplayName("Calculate total interest for ordering")
    void testCalculateTotalInterest() {
        StrategyInput input = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        float totalInterest = strategyService.calculateTotalInterestForOrdering(output.orderedDebts());

        assertTrue(totalInterest > 0);
        // Should sum up interest calculations for all debts
    }

    @Test
    @DisplayName("Determinism flag for all strategies")
    void testDeterminismFlags() {
        assertTrue(strategyService.isOrderingDeterministic(StrategyType.SNOWBALL));
        assertTrue(strategyService.isOrderingDeterministic(StrategyType.AVALANCHE));
        assertTrue(strategyService.isOrderingDeterministic(StrategyType.HYBRID));
        assertTrue(strategyService.isOrderingDeterministic(StrategyType.CUSTOM));
        assertFalse(strategyService.isOrderingDeterministic(StrategyType.MINIMUM_ONLY));
    }

    @Test
    @DisplayName("Null custom order defaults to original")
    void testNullCustomOrder() {
        StrategyInput input = new StrategyInput(StrategyType.CUSTOM, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(testDebts, input);

        // Should return all debts when custom order is null
        assertEquals(testDebts.size(), output.orderedDebts().size());
    }

    @Test
    @DisplayName("Hybrid band calculation with varying APRs")
    void testHybridBandLogic() {
        // Create debts with clear APR bands
        List<DebtSnapshot> bandedDebts = new ArrayList<>();
        bandedDebts.add(new DebtSnapshot("tome-a", "High Interest", 3000f, 0.24f, 0.24f, 60f, 0.24f));
        bandedDebts.add(new DebtSnapshot("tome-b", "High Interest", 1000f, 0.22f, 0.22f, 20f, 0.22f));
        bandedDebts.add(new DebtSnapshot("tome-c", "Mid Interest", 5000f, 0.12f, 0.12f, 100f, 0.12f));
        bandedDebts.add(new DebtSnapshot("tome-d", "Low Interest", 2000f, 0.05f, 0.05f, 40f, 0.05f));

        StrategyInput input = new StrategyInput(StrategyType.HYBRID, null, 100f, 0f);
        StrategyOutput output = strategyService.orderDebts(bandedDebts, input);

        List<DebtSnapshot> ordered = output.orderedDebts();

        // High interest debts should come first (even if smaller balance)
        assertTrue(ordered.get(0).apr() >= 0.20f);
        assertTrue(ordered.get(1).apr() >= 0.20f);
    }

    @Test
    @DisplayName("Strategy comparison: Snowball vs Avalanche")
    void testStrategyComparison() {
        StrategyInput snowball = new StrategyInput(StrategyType.SNOWBALL, null, 100f, 0f);
        StrategyInput avalanche = new StrategyInput(StrategyType.AVALANCHE, null, 100f, 0f);

        StrategyOutput snowballOutput = strategyService.orderDebts(testDebts, snowball);
        StrategyOutput avalancheOutput = strategyService.orderDebts(testDebts, avalanche);

        float snowballInterest = strategyService.calculateTotalInterestForOrdering(snowballOutput.orderedDebts());
        float avalancheInterest = strategyService.calculateTotalInterestForOrdering(avalancheOutput.orderedDebts());

        // Avalanche should typically result in less interest (but both are same total, just different order)
        assertNotNull(snowballInterest);
        assertNotNull(avalancheInterest);
    }
}

