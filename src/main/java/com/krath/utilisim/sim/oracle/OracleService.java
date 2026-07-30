package com.krath.utilisim.sim.oracle;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.chronos.projection.SavingsProjection;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import com.krath.utilisim.sim.ratio.calculate.RatioService;

import java.util.List;

public class OracleService {

    private final RatioService ratioService;

    public OracleService(RatioService ratioService) {
        this.ratioService = ratioService;
    }

    public SavingsProjection compareToBaseline(
            SimulationInput input,
            ScenarioContext scenario,
            List<AmortizationEntry> scenarioSchedule
    ) {

        // Baseline = minimum payment only
        ScenarioContext baselineScenario = new ScenarioContext();
        baselineScenario.setExtraPayment(0f);
        baselineScenario.setLumpSumPayment(0f);
        baselineScenario.setStrategy("MINIMUM_ONLY");

        // Generate baseline schedule
        List<AmortizationEntry> baselineSchedule =
                ratioService.generateSchedule(input, baselineScenario);

        // Calculate interest totals
        float baselineInterest = ratioService.calculateTotalInterest(baselineSchedule);
        float scenarioInterest = ratioService.calculateTotalInterest(scenarioSchedule);

        // Savings = baseline - scenario
        float savings = baselineInterest - scenarioInterest;

        SavingsProjection projection = new SavingsProjection();
        projection.setBaselineInterest(baselineInterest);
        projection.setScenarioInterest(scenarioInterest);
        projection.setSavings(savings);

        return projection;
    }
}
