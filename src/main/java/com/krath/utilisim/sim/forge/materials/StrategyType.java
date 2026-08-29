package com.krath.utilisim.sim.forge.materials;

/**
 * Strategy types for debt repayment ordering.
 * - SNOWBALL: Pay off smallest balance first (psychological wins)
 * - AVALANCHE: Pay off highest interest rate first (mathematical optimal)
 * - HYBRID: Combination of both, prioritize high interest but consider balance
 * - CUSTOM: User-defined ordering by debt priorities
 */
public enum StrategyType {
    SNOWBALL,
    AVALANCHE,
    HYBRID,
    CUSTOM,
    MINIMUM_ONLY // Default: minimum payment only
}

