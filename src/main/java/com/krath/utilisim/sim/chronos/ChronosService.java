package com.krath.utilisim.sim.chronos;

import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * CHRONOS owns the calendar: month starts, display labels and payoff dates.
 * It performs no money math (CURRENT_PLAN_DESIGN §3.2).
 */
@Service
public class ChronosService {

    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMM-yy", Locale.US);

    /** First day of the month for 1-based {@code monthIndex} in the plan. */
    public LocalDate monthStart(LocalDate startMonth, int monthIndex) {
        return startMonth.plusMonths(monthIndex - 1L);
    }

    /** Workbook-style label, e.g. {@code Jun-26}. */
    public String label(LocalDate monthStart) {
        return monthStart.format(MONTH_LABEL);
    }

    // ---- legacy single-balance helpers (kept until /assemble/run is removed)

    public List<AmortizationEntry> applyTimeline(List<AmortizationEntry> schedule) {
        OffsetDateTime start = OffsetDateTime.now();

        for (AmortizationEntry entry : schedule) {
            entry.setDate(start.plusMonths(entry.getMonthIndex()));
        }

        return schedule;
    }

    public OffsetDateTime getPayoffDate(List<AmortizationEntry> schedule) {
        if (schedule.isEmpty()) {
            return OffsetDateTime.now();
        }

        AmortizationEntry last = schedule.get(schedule.size() - 1);
        return last.getDate();
    }
}
