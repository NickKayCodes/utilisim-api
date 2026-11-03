package com.krath.utilisim.sim.forge.materials;

/**
 * Outcome of a simulation run (CURRENT_PLAN_DESIGN §3.5).
 *
 * <p>This is deliberately separate from {@link SimulationStatus}: the status is
 * the lifecycle ({@code PENDING/RUNNING/COMPLETED/FAILED}), while this enum says
 * <em>why</em> the run stopped.</p>
 */
public enum TerminationReason {
    NONE,
    PAID_OFF,
    HORIZON_REACHED,
    STAGNANT;

    public SimulationStatus toStatus() {
        return switch (this) {
            case NONE -> SimulationStatus.RUNNING;
            case PAID_OFF -> SimulationStatus.COMPLETED;
            case HORIZON_REACHED, STAGNANT -> SimulationStatus.FAILED;
        };
    }
}
