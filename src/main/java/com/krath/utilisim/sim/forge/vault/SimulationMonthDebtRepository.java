package com.krath.utilisim.sim.forge.vault;

import com.krath.utilisim.sim.forge.materials.SimulationMonthDebt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationMonthDebtRepository extends JpaRepository<SimulationMonthDebt, Long> {

    List<SimulationMonthDebt> findByRunIdOrderByMonthIndexAscTomeIdAsc(Long runId);
}
