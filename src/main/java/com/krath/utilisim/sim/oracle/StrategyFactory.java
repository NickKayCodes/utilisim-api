package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.sim.forge.materials.StrategyType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strategy Factory - Simplifies strategy creation with predefined configurations.
 * Provides builder pattern for flexible strategy setup.
 */
@Component
public class StrategyFactory {

    /**
     * Create a Snowball strategy - pay off smallest balance first.
     */
    public StrategyInput createSnowball(float extraPayment, float lumpSumPayment) {
        return new StrategyInput(
            StrategyType.SNOWBALL,
            null,
            extraPayment,
            lumpSumPayment
        );
    }

    /**
     * Create an Avalanche strategy - pay off highest interest rate first.
     */
    public StrategyInput createAvalanche(float extraPayment, float lumpSumPayment) {
        return new StrategyInput(
            StrategyType.AVALANCHE,
            null,
            extraPayment,
            lumpSumPayment
        );
    }

    /**
     * Create a Hybrid strategy - balance between interest rate and balance.
     */
    public StrategyInput createHybrid(float extraPayment, float lumpSumPayment) {
        return new StrategyInput(
            StrategyType.HYBRID,
            null,
            extraPayment,
            lumpSumPayment
        );
    }

    /**
     * Create a Custom strategy with user-defined debt ordering.
     */
    public StrategyInput createCustom(
            List<String> debtOrder,
            float extraPayment,
            float lumpSumPayment
    ) {
        return new StrategyInput(
            StrategyType.CUSTOM,
            debtOrder,
            extraPayment,
            lumpSumPayment
        );
    }

    /**
     * Create a Minimum Only strategy - no additional payments.
     */
    public StrategyInput createMinimumOnly() {
        return new StrategyInput(
            StrategyType.MINIMUM_ONLY,
            null,
            0f,
            0f
        );
    }

    /**
     * Builder pattern for custom strategy configurations.
     */
    public static class StrategyBuilder {
        private StrategyType strategyType;
        private List<String> customDebtOrder;
        private float extraPayment = 0f;
        private float lumpSumPayment = 0f;

        public StrategyBuilder strategyType(StrategyType type) {
            this.strategyType = type;
            return this;
        }

        public StrategyBuilder customDebtOrder(List<String> order) {
            this.customDebtOrder = order;
            return this;
        }

        public StrategyBuilder extraPayment(float amount) {
            this.extraPayment = amount;
            return this;
        }

        public StrategyBuilder lumpSumPayment(float amount) {
            this.lumpSumPayment = amount;
            return this;
        }

        public StrategyInput build() {
            if (strategyType == null) {
                throw new IllegalArgumentException("StrategyType is required");
            }
            return new StrategyInput(
                strategyType,
                customDebtOrder,
                extraPayment,
                lumpSumPayment
            );
        }
    }

    /**
     * Get a new StrategyBuilder for fluent API.
     */
    public static StrategyBuilder builder() {
        return new StrategyBuilder();
    }
}

