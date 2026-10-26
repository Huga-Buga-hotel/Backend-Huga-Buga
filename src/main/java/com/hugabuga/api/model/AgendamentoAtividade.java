package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "agendamento_atividade")
@Getter @Setter
public class AgendamentoAtividade {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID reservaId;
    @Column(nullable = false) private UUID sessaoId;
    @Column(nullable = false) private int quantidade;
    /** Vai para a conta da toca (pago no hotel). */
    @Column(nullable = false) private int valorCentavos;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusAgendamento status;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
