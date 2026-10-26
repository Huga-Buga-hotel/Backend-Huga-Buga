package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Preço diferente em datas específicas (feriado). Par (cavernaId, data) único. */
@Entity
@Table(name = "tarifa_especial", uniqueConstraints = @UniqueConstraint(columnNames = {"caverna_id", "data"}))
@Getter @Setter
public class TarifaEspecial {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID cavernaId;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false) private int precoCentavos;
    private String motivo;
}
