package com.krath.utilisim.sim.forge.integration;

import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.sim.forge.anvil.PlanComparisonService;
import com.krath.utilisim.sim.forge.hammer.ComparisonRequest;
import com.krath.utilisim.sim.forge.hammer.ComparisonResponse;
import com.krath.utilisim.sim.forge.hammer.PlanTimeline;
import com.krath.utilisim.sim.forge.materials.PlanType;
import com.krath.utilisim.sim.forge.materials.Simulation;
import com.krath.utilisim.sim.forge.materials.SimulationMonth;
import com.krath.utilisim.sim.forge.materials.SimulationRunDebt;
import com.krath.utilisim.sim.forge.vault.SimulationMonthRepository;
import com.krath.utilisim.sim.forge.vault.SimulationRepository;
import com.krath.utilisim.sim.forge.vault.SimulationRunDebtRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the new simulation path against the in-memory database.
 * The workbook is not available, so this asserts persistence/reproducibility and
 * the documented formulas, not spreadsheet cells.
 */
@SpringBootTest
@Transactional
class SimulationFlowIntegrationTest {

    @Autowired
    private PlanComparisonService service;

    @Autowired
    private Scribe scribe;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SimulationMonthRepository monthRepository;

    @Autowired
    private SimulationRunDebtRepository runDebtRepository;

    private Tome tome(String userId, String name, String balance, String apr, String minimum) {
        Tome tome = new Tome();
        tome.setUserId(userId);
        tome.setName(name);
        tome.setCurrentBalance(new BigDecimal(balance));
        tome.setApr(new BigDecimal(apr));
        tome.setEstimatedApr(new BigDecimal(apr));
        tome.setMinimumDebtPayment(new BigDecimal(minimum));
        tome.setCurrentPayment(new BigDecimal(minimum));
        tome.setCreditLimit(new BigDecimal("5000"));
        tome.setType("CREDIT_CARD");
        tome.setCreatedAt(OffsetDateTime.now());
        tome.setUpdatedAt(OffsetDateTime.now());
        return scribe.save(tome);
    }

    private ComparisonRequest request(List<String> ids) {
        return new ComparisonRequest(
                ids,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                LocalDate.of(2026, 6, 1),
                60,
                null);
    }

    @Test
    @DisplayName("compare persists five plans and returns a derived recommendation")
    void comparePersistsPlans() {
        Tome card = tome("user-1", "Visa", "1200", "12", "100");

        ComparisonResponse response = service.compare(request(List.of(card.getId())), "user-1");

        assertNotNull(response.comparisonGroupId());
        assertEquals(5, response.plans().size());
        assertEquals(new BigDecimal("1200.00"), response.totalBalance());
        assertNotNull(response.recommendedPlan());

        for (PlanTimeline timeline : response.plans()) {
            assertTrue(timeline.runId() > 0);
            assertFalse(timeline.months().isEmpty());
            assertEquals(1, timeline.months().get(0).debts().size());
            assertEquals("Jun-26", timeline.months().get(0).monthLabel());

            Simulation run = simulationRepository.findById(timeline.runId()).orElseThrow();
            assertEquals(timeline.plan(), run.getPlanType());
            assertEquals(timeline.terminationReason(), run.getTerminationReason());
            assertEquals(response.comparisonGroupId(), run.getComparisonGroupId());

            List<SimulationMonth> months =
                    monthRepository.findByRunIdOrderByMonthIndexAsc(timeline.runId());
            assertEquals(timeline.months().size(), months.size());

            List<SimulationRunDebt> snapshot = runDebtRepository.findByRunId(timeline.runId());
            assertEquals(1, snapshot.size());
            assertEquals(0, new BigDecimal("12.0000").compareTo(snapshot.get(0).getApr()));
            assertEquals(0, new BigDecimal("100.00").compareTo(snapshot.get(0).getCurrentPayment()));
            assertEquals(0, new BigDecimal("5000.00").compareTo(snapshot.get(0).getCreditLimit()));
        }
    }

    @Test
    @DisplayName("duplicate tome ids are deduplicated instead of failing the unique snapshot")
    void deduplicatesTomeIds() {
        Tome card = tome("user-dup", "Visa", "1200", "12", "100");

        ComparisonResponse response = service.compare(
                request(List.of(card.getId(), card.getId())), "user-dup");

        assertEquals(5, response.plans().size());
        PlanTimeline first = response.plans().get(0);
        assertEquals(1, first.months().get(0).debts().size());
        assertEquals(1, runDebtRepository.findByRunId(first.runId()).size());
    }

    @Test
    @DisplayName("duplicate plan entries produce one run per plan")
    void deduplicatesPlans() {
        Tome card = tome("user-plans", "Visa", "1200", "12", "100");
        ComparisonRequest request = new ComparisonRequest(
                List.of(card.getId()),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                LocalDate.of(2026, 6, 1),
                60,
                List.of(PlanType.CURRENT, PlanType.CURRENT, PlanType.AVALANCHE));

        ComparisonResponse response = service.compare(request, "user-plans");

        assertEquals(List.of(PlanType.CURRENT, PlanType.AVALANCHE),
                response.plans().stream().map(PlanTimeline::plan).toList());
    }

    @Test
    @DisplayName("a persisted run and comparison can be read back")
    void readsBackPersistedRuns() {
        Tome card = tome("user-2", "Visa", "1200", "12", "100");
        ComparisonResponse response = service.compare(request(List.of(card.getId())), "user-2");

        PlanTimeline first = response.plans().get(0);
        PlanTimeline read = service.getRun(first.runId(), "user-2");
        assertEquals(first.runId(), read.runId());
        assertEquals(first.terminationReason(), read.terminationReason());
        assertEquals(first.months().size(), read.months().size());

        ComparisonResponse group = service.getComparison(response.comparisonGroupId(), "user-2");
        assertEquals(5, group.plans().size());
        assertEquals(response.comparisonGroupId(), group.comparisonGroupId());
    }

    @Test
    @DisplayName("a foreign run is not readable")
    void hidesForeignRuns() {
        Tome card = tome("user-3", "Visa", "1200", "12", "100");
        PlanTimeline run = service.compare(request(List.of(card.getId())), "user-3")
                .plans().get(0);

        assertThrows(NoSuchElementException.class,
                () -> service.getRun(run.runId(), "someone-else"));
    }

    @Test
    @DisplayName("a foreign tome id is rejected")
    void hidesForeignTomes() {
        Tome card = tome("user-4", "Visa", "1200", "12", "100");

        assertThrows(NoSuchElementException.class,
                () -> service.compare(request(List.of(card.getId())), "someone-else"));
    }

    @Test
    @DisplayName("an empty debt set is a validation error")
    void rejectsEmptyDebtSet() {
        assertThrows(IllegalArgumentException.class,
                () -> service.compare(request(List.of()), "user-with-no-accounts"));
    }

    @Test
    @DisplayName("CURRENT and MINIMUM ignore extra and lump-sum inputs")
    void baselinesIgnoreStrategyInputs() {
        Tome card = tome("user-5", "Visa", "1200", "12", "100");

        ComparisonResponse plain = service.compare(request(List.of(card.getId())), "user-5");
        ComparisonRequest withInputs = new ComparisonRequest(
                List.of(card.getId()),
                new BigDecimal("500"),
                new BigDecimal("1000"),
                1,
                LocalDate.of(2026, 6, 1),
                60,
                List.of(PlanType.CURRENT, PlanType.MINIMUM));
        ComparisonResponse boosted = service.compare(withInputs, "user-5");

        assertEquals(new BigDecimal("0.00"),
                boosted.plans().get(0).months().get(0).lumpSumApplied());
        assertEquals(plain.plans().get(0).months().size(), boosted.plans().get(0).months().size());
    }
}
