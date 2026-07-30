package com.krath.utilisim.sim.forge.hammer;

import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class SimulationResult {
    private String id;
    private String simulationInputId;
    private String userId;

    private SimulationStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    // Aggregates
    private float totalInterestPaid;
    private float totalPrincipalPaid;
    private int monthsToPayoff;
    private OffsetDateTime payoffDate;
    private float savingsComparedToBaseline; // vs. minimum-payment-only scenario

    // Detailed schedule
    private List<AmortizationEntry> schedule;

    // Archive + Ledger hooks
    private OffsetDateTime archivedAt;
    private String ledgerEntryId;
}
