package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "membro_tribo")
@Getter @Setter
public class MembroTribo {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID usuarioId;
    @Column(nullable = false) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoHospede tipo;
    /** Obrigatória para criança. A idade exibida é calculada, não armazenada. */
    private LocalDate dataNascimento;
    private String observacoes;
}
