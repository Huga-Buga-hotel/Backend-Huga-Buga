package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "cupom")
@Getter @Setter
public class Cupom {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String codigo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoCupom tipo;
    /** PERCENTUAL: 10 = 10%. VALOR_FIXO: centavos. */
    @Column(nullable = false) private int valor;
    private LocalDate validoAte;
    /** Nulo = ilimitado. */
    private Integer usosMaximos;
    private int usos;
    private boolean ativo = true;
}
