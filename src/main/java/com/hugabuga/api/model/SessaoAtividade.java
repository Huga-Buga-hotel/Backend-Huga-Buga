package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Cada horário com vagas. vagasOcupadas sempre entre 0 e vagasTotais (CHECK no banco). */
@Entity
@Table(name = "sessao_atividade", uniqueConstraints = @UniqueConstraint(columnNames = {"atividade_id", "inicio"}))
@Getter @Setter
public class SessaoAtividade {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID atividadeId;
    @Column(nullable = false) private OffsetDateTime inicio;
    @Column(nullable = false) private int vagasTotais;
    @Column(nullable = false) private int vagasOcupadas;
}
