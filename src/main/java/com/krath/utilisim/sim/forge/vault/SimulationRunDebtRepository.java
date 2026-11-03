package com.krath.utilisim.sim.forge.vault;

import com.krath.utilisim.sim.forge.materials.SimulationRunDebt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationRunDebtRepository extends JpaRepository<SimulationRunDebt, Long> {

    List<SimulationRunDebt> findByRunId(Long runId);
}
