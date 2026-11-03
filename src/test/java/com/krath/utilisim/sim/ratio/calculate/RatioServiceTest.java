package com.krath.utilisim.sim.ratio.calculate;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RatioServiceTest {

    private final RatioService service = new RatioService();

    private SimulationInput input(float principal, float rate, int term, float minimum) {
        SimulationInput input = new SimulationInput();
        input.setPrincipal(principal);
        input.setInterestRate(rate);
        input.setTermMonths(term);
        input.setMinimumPayment(minimum);
        return input;
    }

    private ScenarioContext scenario(float extra, float lumpSum) {
        ScenarioContext scenario = new ScenarioContext();
        scenario.setExtraPayment(extra);
        scenario.setLumpSumPayment(lumpSum);
        return scenario;
    }

    @Test
    @DisplayName("generateSchedule amortizes the balance to zero")
    void generatesAmortizationSchedule() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(1200f, 0.12f, 24, 100f), scenario(0f, 0f));

        assertFalse(schedule.isEmpty());
        assertTrue(schedule.size() <= 24);
        assertEquals(1, schedule.get(0).getMonthIndex());
        assertEquals(12f, schedule.get(0).getInterestPaid(), 0.001f);
        assertEquals(88f, schedule.get(0).getPrincipalPaid(), 0.001f);
        assertEquals(1112f, schedule.get(0).getRemainingBalance(), 0.001f);

        AmortizationEntry last = schedule.get(schedule.size() - 1);
        assertEquals(0f, last.getRemainingBalance(), 0.001f);
        assertEquals(1200f, service.calculateTotalPrincipal(schedule), 0.01f);
        assertTrue(service.calculateTotalInterest(schedule) > 0);
    }

    @Test
    @DisplayName("generateSchedule applies extra payments and finishes sooner")
    void appliesExtraPayment() {
        List<AmortizationEntry> baseline = service.generateSchedule(
                input(1200f, 0.12f, 24, 100f), scenario(0f, 0f));
        List<AmortizationEntry> extra = service.generateSchedule(
                input(1200f, 0.12f, 24, 100f), scenario(50f, 0f));

        assertTrue(extra.size() < baseline.size());
        assertEquals(150f, extra.get(0).getPayment(), 0.001f);
        assertEquals(1200f, service.calculateTotalPrincipal(extra), 0.01f);
    }

    @Test
    @DisplayName("generateSchedule applies a lump sum before the first payment")
    void appliesLumpSum() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(1000f, 0.12f, 24, 100f), scenario(0f, 400f));

        assertEquals(506f, schedule.get(0).getRemainingBalance(), 0.001f);
        assertEquals(0f, schedule.get(schedule.size() - 1).getRemainingBalance(), 0.001f);
    }

    @Test
    @DisplayName("a lump sum that clears the balance produces no schedule")
    void lumpSumClearsBalance() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(1000f, 0.12f, 24, 100f), scenario(0f, 1500f));

        assertTrue(schedule.isEmpty());
        assertEquals(0f, service.calculateTotalInterest(schedule));
        assertEquals(0f, service.calculateTotalPrincipal(schedule));
    }

    @Test
    @DisplayName("a zero principal produces no schedule")
    void zeroPrincipalHasNoSchedule() {
        assertTrue(service.generateSchedule(
                input(0f, 0.12f, 24, 100f), scenario(0f, 0f)).isEmpty());
    }

    @Test
    @DisplayName("the term is a planning horizon: the schedule stops there")
    void stopsAtThePlanningHorizon() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(1000f, 0f, 10, 1f), scenario(0f, 0f));

        assertEquals(10, schedule.size());
        assertEquals(990f, schedule.get(schedule.size() - 1).getRemainingBalance(), 0.001f);
    }

    @Test
    @DisplayName("a long enough term clears the same balance")
    void clearsWithinTheHorizon() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(400f, 0f, 10, 1f), scenario(0f, 0f));
        List<AmortizationEntry> longTerm = service.generateSchedule(
                input(400f, 0f, 2000, 1f), scenario(0f, 0f));

        assertEquals(400, longTerm.size());
        assertEquals(0f, longTerm.get(longTerm.size() - 1).getRemainingBalance(), 0.001f);
        assertTrue(schedule.size() < longTerm.size());
    }

    @Test
    @DisplayName("an unbounded term cannot allocate an unbounded schedule")
    void clampsTheHorizon() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(100000f, 0f, Integer.MAX_VALUE, 1f), scenario(0f, 0f));

        assertEquals(600, schedule.size());
    }

    @Test
    @DisplayName("a payment below the monthly interest emits no rows and keeps the balance")
    void paymentBelowInterestDoesNotReducePrincipal() {
        List<AmortizationEntry> schedule = service.generateSchedule(
                input(1000f, 0.12f, 12, 5f), scenario(0f, 0f));

        // Interest is 10/month; 5 never touches principal, so there is no
        // meaningful schedule. The engine reports the remaining balance.
        assertTrue(schedule.isEmpty());
    }
}
