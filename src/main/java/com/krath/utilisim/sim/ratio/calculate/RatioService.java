package com.krath.utilisim.sim.ratio.calculate;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import com.krath.utilisim.sim.forge.hammer.SimulationInput;
import com.krath.utilisim.sim.ratio.AmortizationEntry;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;

@Service
public class RatioService {

    public List<AmortizationEntry> generateSchedule(
            SimulationInput input,
            ScenarioContext scenario
    ) {
        float balance = input.getPrincipal();
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

        while (balance > 0 && month < input.getTermMonths() * 2) {
            month++;

            float payment = basePayment + extra;
            float interest = balance * monthlyRate;
            float principalPaid = payment - interest;

            if (principalPaid < 0) principalPaid = 0;
            if (principalPaid > balance) principalPaid = balance;

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
