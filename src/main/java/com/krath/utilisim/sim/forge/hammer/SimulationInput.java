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
    private float interestRate;      // annual, e.g. 0.1999f
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
