package com.krath.utilisim.sim.chronos;

import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChronosService {

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
