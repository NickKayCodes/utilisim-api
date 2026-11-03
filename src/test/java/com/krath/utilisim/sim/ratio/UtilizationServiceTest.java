package com.krath.utilisim.sim.ratio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UtilizationServiceTest {

    private final UtilizationService service = new UtilizationService();

    @Test
    @DisplayName("utilization is remaining over limit, as a percentage")
    void computesPercent() {
        assertEquals(new BigDecimal("89.74"),
                service.percent(new BigDecimal("22435.77"), new BigDecimal("25000")));
    }

    @Test
    @DisplayName("utilization is null when there is no usable limit")
    void returnsNullWithoutLimit() {
        assertNull(service.percent(new BigDecimal("100"), null));
        assertNull(service.percent(new BigDecimal("100"), BigDecimal.ZERO));
        assertNull(service.percent(null, new BigDecimal("100")));
    }

    @Test
    @DisplayName("aggregate utilization uses only debts with a limit")
    void aggregatesLimitedDebtsOnly() {
        BigDecimal result = service.aggregatePercent(
                List.of(new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("300")),
                List.of(new BigDecimal("2000"), BigDecimal.ZERO, new BigDecimal("1000")));

        // Only the two limited debts participate: 1300 / 3000 = 43.33%.
        // A debt without a limit has no denominator, so it contributes to neither side.
        assertEquals(new BigDecimal("43.33"), result);
    }

    @Test
    @DisplayName("aggregate utilization is null when no debt has a limit")
    void aggregateNullWithoutLimits() {
        assertNull(service.aggregatePercent(
                List.of(new BigDecimal("100")),
                List.of(BigDecimal.ZERO)));
        assertNull(service.aggregatePercent(List.of(), List.of()));
    }
}
