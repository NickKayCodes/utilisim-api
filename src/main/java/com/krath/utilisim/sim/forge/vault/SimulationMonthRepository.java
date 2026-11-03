package com.krath.utilisim.sim.forge.vault;

import com.krath.utilisim.sim.forge.materials.SimulationMonth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SimulationMonthRepository extends JpaRepository<SimulationMonth, Long> {

    List<SimulationMonth> findByRunIdOrderByMonthIndexAsc(Long runId);
}
