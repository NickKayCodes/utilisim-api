package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyFactoryTest {

    private final StrategyFactory factory = new StrategyFactory();

    @Test
    @DisplayName("preset factories populate the strategy type and payments")
    void createsPresets() {
        StrategyInput snowball = factory.createSnowball(10f, 20f);
        assertEquals(StrategyType.SNOWBALL, snowball.strategyType());
        assertEquals(10f, snowball.extraPayment());
        assertEquals(20f, snowball.lumpSumPayment());
        assertNull(snowball.customDebtOrder());

        assertEquals(StrategyType.AVALANCHE, factory.createAvalanche(0f, 0f).strategyType());
        assertEquals(StrategyType.HYBRID, factory.createHybrid(0f, 0f).strategyType());
        assertEquals(StrategyType.MINIMUM_ONLY, factory.createMinimumOnly().strategyType());
        assertEquals(0f, factory.createMinimumOnly().extraPayment());
    }

    @Test
    @DisplayName("createCustom carries the user-defined order")
    void createsCustom() {
        StrategyInput custom = factory.createCustom(List.of("t2", "t1"), 5f, 6f);

        assertEquals(StrategyType.CUSTOM, custom.strategyType());
        assertEquals(List.of("t2", "t1"), custom.customDebtOrder());
        assertEquals(5f, custom.extraPayment());
        assertEquals(6f, custom.lumpSumPayment());
    }

    @Test
    @DisplayName("the builder creates a fully populated strategy")
    void buildsStrategy() {
        StrategyInput input = StrategyFactory.builder()
                .strategyType(StrategyType.CUSTOM)
                .customDebtOrder(List.of("t1", "t2"))
                .extraPayment(15f)
                .lumpSumPayment(25f)
                .build();

        assertEquals(StrategyType.CUSTOM, input.strategyType());
        assertEquals(List.of("t1", "t2"), input.customDebtOrder());
        assertEquals(15f, input.extraPayment());
        assertEquals(25f, input.lumpSumPayment());
    }

    @Test
    @DisplayName("the builder defaults payments to zero")
    void buildsWithDefaults() {
        StrategyInput input = StrategyFactory.builder()
                .strategyType(StrategyType.SNOWBALL)
                .build();

        assertEquals(0f, input.extraPayment());
        assertEquals(0f, input.lumpSumPayment());
        assertNull(input.customDebtOrder());
    }

    @Test
    @DisplayName("the builder requires a strategy type")
    void builderRequiresType() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> StrategyFactory.builder().build());

        assertEquals("StrategyType is required", ex.getMessage());
    }
}
