package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Catálogo do passo 2. Id textual (ex.: KIT_MINI_CACADOR). */
@Entity
@Table(name = "extra")
@Getter @Setter
public class Extra {
    @Id private String id;
    @Column(nullable = false) private String nome;
    private String descricao;
    private String icone;
    @Column(nullable = false) private int precoCentavos;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CobrancaExtra cobranca;
    /** Já vem em toda reserva (ex.: café jurássico). */
    private boolean incluso;
    private boolean ativo = true;
}
