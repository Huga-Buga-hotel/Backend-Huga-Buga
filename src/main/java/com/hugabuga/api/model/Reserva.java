package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Centro do sistema: nasce PENDENTE no passo 1 e vira o voucher. Valores sempre em centavos. */
@Entity
@Table(name = "reserva")
@Getter @Setter
public class Reserva {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    /** Formato HB-XXXX. */
    @Column(nullable = false, unique = true) private String codigo;
    /** Opcional: reserva sem conta. */
    private UUID usuarioId;
    @Column(nullable = false) private UUID cavernaId;
    @Column(nullable = false) private UUID unidadeId;

    @Column(nullable = false) private LocalDate checkIn;
    @Column(nullable = false) private LocalDate checkOut;
    @Column(nullable = false) private int adultos;
    private int criancas;

    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusReserva status;
    @Column(nullable = false) private OffsetDateTime expiraEm;
    /** Já usou o "+10 min". */
    private boolean prorrogada;

    // Responsável
    private String responsavelNome;
    private String responsavelEmail;
    private String responsavelCelular;
    @Column(length = 2) private String responsavelPais;
    private String responsavelTipoDocumento;
    private String responsavelDocumento;

    @Column(columnDefinition = "text") private String pedidoEspecial;
    private boolean lateCheckout;

    // Valores em centavos
    private int valorDiarias;
    private int valorExtras;
    /** Desconto do cupom. */
    private int desconto;
    /** Quantidade de ossos (10 ossos = R$ 1). */
    private int ossosUsados;
    private int total;

    private UUID cupomId;
    private OffsetDateTime checkinOnlineEm;
    private OffsetDateTime canceladaEm;
    @Column(nullable = false) private OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;
}
