package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Horários de início do jantar (19:00 a 21:30). A capacidade é do salão: hugabuga.restaurante.capacidade-pessoas. */
@Entity
@Table(name = "horario_mesa")
@Getter @Setter
public class HorarioMesa {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private LocalTime horario;
}
