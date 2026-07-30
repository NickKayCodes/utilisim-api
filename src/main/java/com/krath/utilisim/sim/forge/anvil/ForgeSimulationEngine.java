package com.krath.utilisim.sim.forge.anvil;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.ChronosService;
import com.krath.utilisim.sim.chronos.projection.SavingsProjection;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.hammer.SimulationResult;
import com.krath.utilisim.sim.forge.materials.SimulationStatus;
import com.krath.utilisim.sim.oracle.OracleService;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ForgeSimulationEngine {

    private final RatioService ratioService;
    private final ChronosService chronosService;
    private final OracleService oracleService;

    public ForgeSimulationEngine(RatioService ratioService, ChronosService chronosService, OracleService oracleService) {
        this.ratioService = ratioService;
        this.chronosService = chronosService;
        this.oracleService = oracleService;
    }

    public SimulationResult run(SimulationInput input, ScenarioContext scenario) {

        SimulationResult result = new SimulationResult();
        result.setId(UUID.randomUUID().toString());
        result.setSimulationInputId(input.getId());
        result.setUserId(input.getUserId());
        result.setStatus(SimulationStatus.RUNNING);
        result.setStartedAt(OffsetDateTime.now());

        // 1. Delegate math to Ratio
        List<AmortizationEntry> rawSchedule =
                ratioService.generateSchedule(input, scenario);

        float totalInterest = ratioService.calculateTotalInterest(rawSchedule);
        float totalPrincipal = ratioService.calculateTotalPrincipal(rawSchedule);

        // 2. Delegate time to Chronos
        List<AmortizationEntry> timeline =
                chronosService.applyTimeline(rawSchedule);

        OffsetDateTime payoffDate =
                chronosService.getPayoffDate(timeline);

        int monthsToPayoff = timeline.size();

        // 3. Delegate projections to Oracle
        SavingsProjection projection =
                oracleService.compareToBaseline(input, scenario, timeline);

        // 4. Assemble SimulationResult
        result.setSchedule(timeline);
        result.setTotalInterestPaid(totalInterest);
        result.setTotalPrincipalPaid(totalPrincipal);
        result.setMonthsToPayoff(monthsToPayoff);
        result.setPayoffDate(payoffDate);
        result.setSavingsComparedToBaseline(projection.getSavings());

        result.setStatus(SimulationStatus.COMPLETED);
        result.setCompletedAt(OffsetDateTime.now());

        return result;
    }
}

