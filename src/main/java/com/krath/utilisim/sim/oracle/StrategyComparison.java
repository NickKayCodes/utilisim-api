package com.krath.utilisim.sim.oracle;

/**
 * DTO for comparing a single strategy's effectiveness.
 */
public record StrategyComparison(
    String strategyName,
    float projectedInterest,
    int debtCount,
    String description
) {}

