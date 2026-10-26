package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "hospede_reserva")
@Getter @Setter
public class HospedeReserva {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID reservaId;
    @Column(nullable = false) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoHospede tipo;
    /** 0 a 17; obrigatória para criança. */
    private Integer idade;
}
