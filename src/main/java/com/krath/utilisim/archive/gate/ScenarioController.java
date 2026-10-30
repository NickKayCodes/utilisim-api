package com.krath.utilisim.archive.gate;

import com.krath.utilisim.archive.datastate.ScenarioRepository;
import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sentinel.foundation.AppUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scenario CRUD, scoped to the authenticated owner.
 *
 * <p>Ownership is derived from the security principal, never from the request body.
 * A row that belongs to another user is indistinguishable from a missing one: both
 * return 404, so ids cannot be probed for existence.</p>
 */
@RestController
@RequestMapping("/api/archive")
public class ScenarioController {

    private final ScenarioRepository scenarioRepository;

    public ScenarioController(ScenarioRepository scenarioRepository) {
        this.scenarioRepository = scenarioRepository;
    }

    // GET /api/archive/scenarios
    @GetMapping("/scenarios")
    public List<ScenarioContext> getAllScenarios(@AuthenticationPrincipal AppUser user) {
        return scenarioRepository.findByUserId(user.getId());
    }

    // POST /api/archive/scenarios
    @PostMapping("/scenarios")
    public ScenarioContext createScenario(
            @AuthenticationPrincipal AppUser user,
            @RequestBody ScenarioContext scenarioContext) {
        OffsetDateTime now = OffsetDateTime.now();
        // A client-supplied id must never select an existing row: saving with a
        // foreign id would overwrite another user's scenario.
        scenarioContext.setId(null);
        scenarioContext.setUserId(user.getId());
        scenarioContext.setCreatedAt(now);
        scenarioContext.setUpdatedAt(now);
        return scenarioRepository.save(scenarioContext);
    }

    // GET /api/archive/scenarios/:id
    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioContext> getScenarioById(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String id) {
        return scenarioRepository.findByIdAndUserId(id, user.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // PUT /api/archive/scenarios/:id
    @PutMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioContext> updateScenario(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String id,
            @RequestBody ScenarioContext scenarioContext) {
        return scenarioRepository.findByIdAndUserId(id, user.getId())
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
    public ResponseEntity<Void> deleteScenario(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String id) {
        if (!scenarioRepository.existsByIdAndUserId(id, user.getId())) {
            return ResponseEntity.notFound().build();
        }
        scenarioRepository.deleteByIdAndUserId(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
