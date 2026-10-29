package com.krath.utilisim.sim.chronos;

import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChronosServiceTest {

    private final ChronosService service = new ChronosService();

    private AmortizationEntry entry(int month) {
        AmortizationEntry entry = new AmortizationEntry();
        entry.setMonthIndex(month);
        return entry;
    }

    @Test
    @DisplayName("applyTimeline assigns increasing dates")
    void appliesTimeline() {
        List<AmortizationEntry> schedule = new ArrayList<>(List.of(entry(1), entry(2), entry(3)));

        List<AmortizationEntry> result = service.applyTimeline(schedule);

        assertSame(schedule, result);
        assertTrue(result.stream().allMatch(entry -> entry.getDate() != null));
        assertTrue(result.get(0).getDate().isBefore(result.get(1).getDate()));
        assertTrue(result.get(1).getDate().isBefore(result.get(2).getDate()));
    }

    @Test
    @DisplayName("getPayoffDate returns the last scheduled date")
    void returnsLastDate() {
        List<AmortizationEntry> schedule = service.applyTimeline(
                new ArrayList<>(List.of(entry(1), entry(2))));

        assertEquals(schedule.get(1).getDate(), service.getPayoffDate(schedule));
    }

    @Test
    @DisplayName("getPayoffDate falls back to now for an empty schedule")
    void returnsNowForEmptySchedule() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        OffsetDateTime payoff = service.getPayoffDate(new ArrayList<>());

        assertNotNull(payoff);
        assertTrue(payoff.isAfter(before));
    }
}
