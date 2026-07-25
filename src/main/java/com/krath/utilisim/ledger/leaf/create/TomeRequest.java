package com.krath.utilisim.ledger.leaf.CreateTome;

public record TomeRequest(
        String userId, // fk to AppUser
        String name, // Acc name
        float currentBalance, //current balance
        float apr, //interest rate that is user entered but can be nullable
        float estimatedApr, //if apr is null, system will calculate an estimated apr (not accurate)
        float minimumDebtPayment,
        float creditLimit,
        String type // Line of credit type.
) {}