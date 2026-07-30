package com.krath.utilisim.sim.forge.controller;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.forge.anvil.ForgeSimulationEngine;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.forge.hammer.SimulationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assemble")
public class AssembleController {

    @Autowired
    private ForgeSimulationEngine forgeSimulationEngine;

    @PostMapping("/run")
    public SimulationResult runSimulation(@RequestBody SimulationInput input) {

        // Load scenario context (you will wire this later)
        ScenarioContext scenario = new ScenarioContext();
        scenario.setExtraPayment(input.getExtraPayment());
        scenario.setLumpSumPayment(input.getLumpSumPayment());
        scenario.setStrategy(input.getStrategy());

        // Forge handles Ratio + Chronos + Oracle internally
        return forgeSimulationEngine.run(input, scenario);
    }
}
