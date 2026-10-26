package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "movimento_ossos")
@Getter @Setter
public class MovimentoOssos {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID usuarioId;
    private UUID reservaId;
    /** Positivo ganha, negativo usa. */
    @Column(nullable = false) private int quantidade;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MotivoOssos motivo;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
