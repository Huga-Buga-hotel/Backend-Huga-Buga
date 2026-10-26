package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "atividade")
@Getter @Setter
public class Atividade {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CategoriaAtividade categoria;
    @Column(columnDefinition = "text") private String descricao;
    private int duracaoMin;
    private Integer idadeMin;
    private Integer idadeMax;
    /** 0 = Grátis. */
    private int precoCentavos;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CobrancaAtividade cobranca;
    /** Ex.: "MAIS AMADA PELAS CRIANÇAS". */
    private String destaque;
    private boolean ativa = true;
}
