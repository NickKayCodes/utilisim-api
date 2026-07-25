package com.krath.utilisim.archive.gate;

import com.krath.utilisim.archive.datastate.ScenarioRepository;
import com.krath.utilisim.archive.scenario.ScenarioContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/archive")
public class ScenarioController {

    private final ScenarioRepository scenarioRepository;

    public ScenarioController(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    // GET /api/archive/scenarios
    @GetMapping("/scenarios")
    public List<ScenarioContext> getAllScenarios() {
        return scenarioRepository.findAll();
    }

    // POST /api/archive/scenarios
    @PostMapping("/scenarios")
    public ScenarioContext createScenario(@RequestBody ScenarioContext scenarioContext) {
        scenarioContext.setCreatedAt(OffsetDateTime.now());
        scenarioContext.setUpdatedAt(OffsetDateTime.now());
        return scenarioRepository.save(scenarioContext);
    }

    // GET /api/archive/scenarios/:id
    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioContext> getScenarioById(@PathVariable String id) {
        return scenarioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT /api/archive/scenarios/:id
    @PutMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioContext> updateScenario(@PathVariable String id, @RequestBody ScenarioContext scenarioContext) {
        return scenarioRepository.findById(id)
                .map(existingScenario -> {
                    existingScenario.setName(scenarioContext.getName());
                    existingScenario.setStrategy(scenarioContext.getStrategy());
                    existingScenario.setExtraPayment(scenarioContext.getExtraPayment());
                    existingScenario.setLumpSumPayment(scenarioContext.getLumpSumPayment());
                    existingScenario.setUpdatedAt(OffsetDateTime.now());
                    return ResponseEntity.ok(scenarioRepository.save(existingScenario));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // DELETE /api/archive/scenarios/:id
    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String id) {
        if (scenarioRepository.existsById(id)) {
            scenarioRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}