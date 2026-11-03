package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.SimulationType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class SimulationInput {

    private String id;
    private String userId;
    private SimulationType type;

    // Core debt parameters
    private float principal;
    /**
     * Annual rate as a <b>decimal</b>: {@code 0.1999} means 19.99%.
     * This is the one place the API does not use percentages; see
     * {@code AprRates} and the contracts before changing it.
     */
    private float interestRate;
    /**
     * Planning horizon in months. The schedule stops here; if money is still
     * owed the run is reported as {@code FAILED} rather than {@code COMPLETED}.
     */
    private int termMonths;          // total term
    private float minimumPayment;

    // Scenario context (from ScenarioContext)
    private String scenarioId;
    private String strategy;         // e.g. "AVALANCHE", "SNOWBALL", "MIN_INTEREST"
    private float extraPayment;      // recurring extra
    private float lumpSumPayment;    // one-time injection

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

}
