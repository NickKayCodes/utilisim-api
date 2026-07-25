package com.krath.utilisim.ledger.tome;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tome_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Tome {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String userId; //fk to AppUser
    private String name; // Acc name
    private float currentBalance; //current balance

    @Column(nullable = true)
    private float apr; //interest rate that is user entered but can be nullable
    private float estimatedApr; //if apr is null, system will calculate an estimated apr (not accurate)
    private float minimumDebtPayment;
    private float creditLimit;
    private String type; // Line of credit type.
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
