package com.krath.utilisim.sim.oracle.vault;

import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.sim.forge.materials.StrategyType;
import com.krath.utilisim.sim.oracle.DebtSnapshot;
import com.krath.utilisim.sim.oracle.StrategyInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyMapperTest {

    private final StrategyMapper mapper = new StrategyMapper();

    private Tome tome(float apr, float estimatedApr) {
        Tome tome = new Tome();
        tome.setId("t1");
        tome.setName("Visa");
        tome.setCurrentBalance(1200f);
        tome.setApr(apr);
        tome.setEstimatedApr(estimatedApr);
        tome.setMinimumDebtPayment(35f);
        return tome;
    }

    @Test
    @DisplayName("toDebtSnapshot prefers a real APR")
    void mapsTomeWithApr() {
        DebtSnapshot snapshot = mapper.toDebtSnapshot(tome(22f, 16f));

        assertEquals("t1", snapshot.tomeId());
        assertEquals("Visa", snapshot.accountName());
        assertEquals(1200f, snapshot.currentBalance());
        assertEquals(22f, snapshot.apr());
        assertEquals(16f, snapshot.estimatedApr());
        assertEquals(35f, snapshot.minimumPayment());
        assertEquals(22f, snapshot.interestRate());
    }

    @Test
    @DisplayName("toDebtSnapshot uses the estimated APR when the real one is missing")
    void mapsTomeWithEstimatedApr() {
        DebtSnapshot snapshot = mapper.toDebtSnapshot(tome(0f, 16f));

        assertEquals(16f, snapshot.interestRate());
    }

    @Test
    @DisplayName("toDebtSnapshots maps every tome")
    void mapsTomeList() {
        List<DebtSnapshot> snapshots = mapper.toDebtSnapshots(List.of(tome(22f, 16f), tome(0f, 16f)));

        assertEquals(2, snapshots.size());
        assertEquals(22f, snapshots.get(0).interestRate());
        assertEquals(16f, snapshots.get(1).interestRate());
    }

    @Test
    @DisplayName("toStrategyInput parses a stored custom order")
    void parsesCustomOrder() {
        StrategyConfig config = new StrategyConfig();
        config.setStrategyType(StrategyType.CUSTOM);
        config.setCustomDebtOrder("[\"t2\",\"t1\"]");
        config.setExtraPayment(10f);
        config.setLumpSumPayment(20f);

        StrategyInput input = mapper.toStrategyInput(config);

        assertEquals(StrategyType.CUSTOM, input.strategyType());
        assertEquals(List.of("t2", "t1"), input.customDebtOrder());
        assertEquals(10f, input.extraPayment());
        assertEquals(20f, input.lumpSumPayment());
    }

    @Test
    @DisplayName("toStrategyInput treats missing, blank and malformed JSON as empty")
    void toleratesBadCustomOrder() {
        StrategyConfig missing = new StrategyConfig();
        missing.setStrategyType(StrategyType.SNOWBALL);
        assertTrue(mapper.toStrategyInput(missing).customDebtOrder().isEmpty());

        StrategyConfig blank = new StrategyConfig();
        blank.setStrategyType(StrategyType.SNOWBALL);
        blank.setCustomDebtOrder("   ");
        assertTrue(mapper.toStrategyInput(blank).customDebtOrder().isEmpty());

        StrategyConfig malformed = new StrategyConfig();
        malformed.setStrategyType(StrategyType.SNOWBALL);
        malformed.setCustomDebtOrder("[not-json");
        assertTrue(mapper.toStrategyInput(malformed).customDebtOrder().isEmpty());
    }

    @Test
    @DisplayName("toStrategyConfig serializes a custom order and stamps timestamps")
    void mapsStrategyConfig() {
        StrategyInput input = new StrategyInput(
                StrategyType.CUSTOM, List.of("t2", "t1"), 10f, 20f);

        StrategyConfig config = mapper.toStrategyConfig(input, "u1", "s1", "because");

        assertEquals("u1", config.getUserId());
        assertEquals("s1", config.getScenarioId());
        assertEquals(StrategyType.CUSTOM, config.getStrategyType());
        assertEquals("[\"t2\",\"t1\"]", config.getCustomDebtOrder());
        assertEquals(10f, config.getExtraPayment());
        assertEquals(20f, config.getLumpSumPayment());
        assertEquals("because", config.getDescription());
        assertNotNull(config.getCreatedAt());
        assertNotNull(config.getUpdatedAt());
    }

    @Test
    @DisplayName("toStrategyConfig leaves an empty custom order null")
    void mapsEmptyCustomOrder() {
        StrategyInput nullOrder = new StrategyInput(StrategyType.SNOWBALL, null, 0f, 0f);
        StrategyInput emptyOrder = new StrategyInput(StrategyType.SNOWBALL, List.of(), 0f, 0f);

        assertNull(mapper.toStrategyConfig(nullOrder, "u1", "s1", "x").getCustomDebtOrder());
        assertNull(mapper.toStrategyConfig(emptyOrder, "u1", "s1", "x").getCustomDebtOrder());
    }

    @Test
    @DisplayName("toStrategyConfig falls back to null when serialization fails")
    void toleratesSerializationFailure() throws JsonProcessingException {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        ReflectionTestUtils.setField(mapper, "objectMapper", failing);

        StrategyConfig config = mapper.toStrategyConfig(
                new StrategyInput(StrategyType.CUSTOM, List.of("t1"), 0f, 0f),
                "u1", "s1", "x");

        assertNull(config.getCustomDebtOrder());
    }
}
