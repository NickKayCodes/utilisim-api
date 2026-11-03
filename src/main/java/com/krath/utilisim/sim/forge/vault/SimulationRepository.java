package com.krath.utilisim.sim.forge.vault;

import com.krath.utilisim.sim.forge.materials.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, Long> {

    List<Simulation> findByUserIdOrderByIdDesc(String userId);

    List<Simulation> findByComparisonGroupIdOrderByIdAsc(UUID comparisonGroupId);

    Optional<Simulation> findByIdAndUserId(Long id, String userId);
}
