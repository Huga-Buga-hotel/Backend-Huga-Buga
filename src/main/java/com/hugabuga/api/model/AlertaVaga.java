package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Botão "Avisar". Opcional: só grava o pedido. */
@Entity
@Table(name = "alerta_vaga")
@Getter @Setter
public class AlertaVaga {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID usuarioId;
    @Column(nullable = false) private String email;
    /** CAVERNA ou ATIVIDADE. */
    @Column(nullable = false) private String tipo;
    private UUID cavernaId;
    private UUID sessaoId;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private Integer pessoas;
    private OffsetDateTime notificadoEm;
}
