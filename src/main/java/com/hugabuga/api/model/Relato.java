package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Avaliação pós-estadia. Um relato por reserva. */
@Entity
@Table(name = "relato")
@Getter @Setter
public class Relato {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID cavernaId;
    @Column(nullable = false, unique = true) private UUID reservaId;
    @Column(nullable = false) private UUID usuarioId;
    /** 1 a 5 (CHECK no banco). */
    @Column(nullable = false) private int nota;
    @Column(columnDefinition = "text") private String texto;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
