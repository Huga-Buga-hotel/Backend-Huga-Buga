package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Cada tentativa de pagar (uma reserva pode ter várias). Nunca guarda número completo nem CVV. */
@Entity
@Table(name = "pagamento")
@Getter @Setter
public class Pagamento {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID reservaId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MetodoPagamento metodo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusPagamento status;
    @Column(nullable = false) private int valorCentavos;
    /** 1 a 6, só cartão. */
    private Integer parcelas;
    @Column(length = 4) private String cartaoFinal;
    private String cartaoBandeira;
    @Column(columnDefinition = "text") private String pixCopiaCola;
    private OffsetDateTime pixExpiraEm;
    private String motivoRecusa;
    @Column(nullable = false) private OffsetDateTime criadoEm;
    private OffsetDateTime aprovadoEm;
}
