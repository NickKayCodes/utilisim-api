package com.krath.utilisim.sim.forge.materials;

/**
 * The five plans the simulation engine supports (CURRENT_PLAN_DESIGN §3.3).
 *
 * <p>{@code CUSTOM} is deliberately absent from this enum. The legacy
 * {@link StrategyType#CUSTOM} ordering exists in the Oracle API but is not a
 * simulation plan.</p>
 */
public enum PlanType {
    CURRENT,
    MINIMUM,
    SNOWBALL,
    AVALANCHE,
    HYBRID;

    public boolean isStrategy() {
        return this == SNOWBALL || this == AVALANCHE || this == HYBRID;
    }

    /**
     * ORACLE's ordering input. {@code CURRENT} has no ordering because each
     * debt pays its own current payment; {@code MINIMUM} maps for completeness
     * but the engine does not call ORACLE for it.
     */
    public StrategyType toStrategyType() {
        return switch (this) {
            case CURRENT -> null;
            case MINIMUM -> StrategyType.MINIMUM_ONLY;
            case SNOWBALL -> StrategyType.SNOWBALL;
            case AVALANCHE -> StrategyType.AVALANCHE;
            case HYBRID -> StrategyType.HYBRID;
        };
    }
}
