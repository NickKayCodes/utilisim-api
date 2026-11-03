package com.krath.utilisim.sim.ratio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the APR unit mix-up (DEFECTS API-05/API-06): percentages
 * and decimals must only meet through this class.
 */
class AprRatesTest {

    @Test
    @DisplayName("a percent APR converts to its decimal value")
    void convertsPercentToDecimal() {
        assertEquals(0.1999f, AprRates.toDecimal(19.99f), 0.00001f);
        assertEquals(0f, AprRates.toDecimal(0f));
        assertEquals(1f, AprRates.toDecimal(100f), 0.00001f);
    }

    @Test
    @DisplayName("a percent APR converts to a monthly decimal rate")
    void convertsPercentToMonthly() {
        assertEquals(0.016658333f, AprRates.toMonthlyFromPercent(19.99f), 0.000001f);
        assertEquals(0.01f, AprRates.toMonthlyFromPercent(12f), 0.000001f);
    }

    @Test
    @DisplayName("a decimal annual rate converts to a monthly decimal rate")
    void convertsDecimalToMonthly() {
        assertEquals(0.01f, AprRates.toMonthlyFromDecimal(0.12f), 0.000001f);
        assertEquals(0f, AprRates.toMonthlyFromDecimal(0f));
    }

    @Test
    @DisplayName("the effective APR prefers the entered rate and falls back to the estimate")
    void selectsEffectiveApr() {
        assertEquals(19.99f, AprRates.effectivePercent(19.99f, 16f));
        assertEquals(16f, AprRates.effectivePercent(0f, 16f));
        assertEquals(0f, AprRates.effectivePercent(0f, 0f));
    }
}
