package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Cada toca física (T-07, T-08...). Permite o selo "Últimas 2!". */
@Entity
@Table(name = "unidade_caverna")
@Getter @Setter
public class UnidadeCaverna {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID cavernaId;
    @Column(nullable = false, unique = true) private String codigo;
    private boolean ativa = true;
}
