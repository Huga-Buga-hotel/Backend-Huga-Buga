package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** "Uga! Reservar mesa". Só hóspede com reserva confirmada. */
@Entity
@Table(name = "reserva_mesa")
@Getter @Setter
public class ReservaMesa {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    /** Formato BQ-XXXX. */
    @Column(nullable = false, unique = true) private String codigo;
    private UUID usuarioId;
    @Column(nullable = false) private UUID reservaId;
    private String nomeContato;
    private String celularContato;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false) private LocalTime horario;
    /** 1 a 12. */
    @Column(nullable = false) private int pessoas;
    private int cadeirinhas;
    private boolean pertoFogueira;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusMesa status;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
