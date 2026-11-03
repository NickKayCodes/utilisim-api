package com.krath.utilisim.sim.ratio.calculate;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;

@Service
public class RatioService {

    /**
     * Hard ceiling on the number of simulated months.
     *
     * <p>{@code termMonths} is supplied by the client and is not bounded, so a
     * bad request must not be able to allocate an unbounded schedule.</p>
     */
    static final int MAX_MONTHS = 600;

    public List<AmortizationEntry> generateSchedule(
            SimulationInput input,
            ScenarioContext scenario
    ) {
        float balance = input.getPrincipal();
        // interestRate is a decimal annual rate (0.1999 = 19.99%): divide by 12.
        float monthlyRate = input.getInterestRate() / 12.0f;

        float basePayment = input.getMinimumPayment();
        float extra = scenario.getExtraPayment();
        float lumpSum = scenario.getLumpSumPayment();

        List<AmortizationEntry> schedule = new ArrayList<>();

        // Apply lump sum immediately
        if (lumpSum > 0) {
            balance -= lumpSum;
            if (balance < 0) balance = 0;
        }

        int month = 0;
        int horizon = Math.min(input.getTermMonths(), MAX_MONTHS);

        // The term is the planning horizon: a plan that still owes money after
        // termMonths has not paid the debt off, and the caller reports that as a
        // failure rather than a completed payoff.
        while (balance > 0 && month < horizon) {
            month++;

            float payment = basePayment + extra;
            float interest = balance * monthlyRate;
            float principalPaid = payment - interest;

            if (principalPaid < 0) principalPaid = 0;
            if (principalPaid > balance) principalPaid = balance;

            // A payment that only covers interest never reduces the debt. Stop
            // instead of emitting an identical row for every remaining month.
            if (principalPaid <= 0) {
                break;
            }

            balance -= principalPaid;

            AmortizationEntry entry = new AmortizationEntry();
            entry.setMonthIndex(month);
            entry.setPayment(payment);
            entry.setInterestPaid(interest);
            entry.setPrincipalPaid(principalPaid);
            entry.setRemainingBalance(balance);

            schedule.add(entry);
        }

        return schedule;
    }

    public float calculateTotalInterest(List<AmortizationEntry> schedule) {
        return schedule.stream()
                .map(AmortizationEntry::getInterestPaid)
                .reduce(0f, Float::sum);
    }

    public float calculateTotalPrincipal(List<AmortizationEntry> schedule) {
        return schedule.stream()
                .map(AmortizationEntry::getPrincipalPaid)
                .reduce(0f, Float::sum);
    }
}
