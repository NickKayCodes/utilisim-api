package com.krath.utilisim.archive.datastate;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioRepository extends JpaRepository<ScenarioContext, String> {
    List<ScenarioContext> findByUserId(String userId);
}
