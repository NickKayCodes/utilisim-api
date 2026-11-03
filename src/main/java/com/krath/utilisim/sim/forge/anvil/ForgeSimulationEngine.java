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

    /** A balance at or below this counts as paid off (sub-cent rounding noise). */
    static final float REPAID_EPSILON = 0.005f;

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

        int monthsSimulated = timeline.size();

        float remaining = remainingBalance(timeline, input, scenario);
        boolean repaid = remaining <= REPAID_EPSILON;

        // 3. Delegate projections to Oracle
        SavingsProjection projection =
                oracleService.compareToBaseline(input, scenario, timeline);

        // 4. Assemble SimulationResult
        result.setSchedule(timeline);
        result.setTotalInterestPaid(totalInterest);
        result.setTotalPrincipalPaid(totalPrincipal);
        result.setSavingsComparedToBaseline(repaid ? projection.getSavings() : 0f);

        if (repaid) {
            result.setStatus(SimulationStatus.COMPLETED);
            result.setMonthsToPayoff(monthsSimulated);
            result.setPayoffDate(payoffDate);
            result.setRemainingBalance(0f);
        } else {
            // The plan ran out of term with money still owed, or the payment
            // never touched principal. Reporting COMPLETED here was the defect
            // (DEFECTS API-04).
            result.setStatus(SimulationStatus.FAILED);
            result.setMonthsToPayoff(0);
            result.setPayoffDate(null);
            result.setRemainingBalance(remaining);
        }

        result.setCompletedAt(OffsetDateTime.now());

        return result;
    }

    /**
     * Balance still owed at the end of the run.
     *
     * <p>An empty schedule is ambiguous: it means either "nothing was owed"
     * (zero principal, or a lump sum cleared it) or "no payment ever reduced the
     * principal". Both cases must not be reported as a completed payoff.</p>
     */
    private static float remainingBalance(
            List<AmortizationEntry> timeline,
            SimulationInput input,
            ScenarioContext scenario) {
        if (!timeline.isEmpty()) {
            return timeline.get(timeline.size() - 1).getRemainingBalance();
        }
        float lumpSum = Math.max(0f, scenario.getLumpSumPayment());
        return Math.max(0f, input.getPrincipal() - lumpSum);
    }
}
