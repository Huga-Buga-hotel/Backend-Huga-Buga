package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/**
 * "Uga! Reservar mesa". Três origens:
 *  - HOSPEDE: hóspede com estadia confirmada (tem desconto);
 *  - COMBO: criada pelo combo quarto + Banquete, uma por noite (nasce PENDENTE junto com a reserva);
 *  - EXTERNO: visitante de fora, sem estadia (reservaId nulo, e-mail de contato obrigatório).
 */
@Entity
@Table(name = "reserva_mesa")
@Getter @Setter
public class ReservaMesa {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    /** Formato BQ-XXXX. */
    @Column(nullable = false, unique = true) private String codigo;
    private UUID usuarioId;
    /** Nulo quando a origem é EXTERNO. */
    private UUID reservaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrigemMesa origem;
    private String nomeContato;
    private String emailContato;
    private String celularContato;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false) private LocalTime horario;
    /** 1 a 12 na reserva avulsa; o combo pode levar a tribo inteira. */
    @Column(nullable = false) private int pessoas;
    private int cadeirinhas;
    private boolean pertoFogueira;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusMesa status;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
