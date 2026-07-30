package com.krath.utilisim.sim.forge.materials;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "simulation")
@Getter
@Setter
public class Simulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String userId;

    @Enumerated(EnumType.STRING)
    private SimulationType type;
    @Enumerated(EnumType.STRING)
    private SimulationStatus status;

    private String inputId;
    private String resultId;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
