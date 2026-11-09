package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "usuario")
@Getter @Setter
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private String nome;
    @Column(nullable = false) private String sobrenome;
    @Column(nullable = false, unique = true) private String email;
    /** BCrypt. Nunca sai na API. */
    @Column(nullable = false) private String senhaHash;
    private String celular;
    @Column(length = 2) private String pais;
    /** CPF ou PASSAPORTE. */
    private String tipoDocumento;
    private String numeroDocumento;
    private LocalDate dataNascimento;
    private boolean aceitaOfertas;
    /** VISITANTE (hóspede/cliente) ou ADMIN (equipe do hotel). Vai no token JWT. */
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role = Role.VISITANTE;
    /** pt-BR, en ou es. */
    @Column(nullable = false, length = 5) private String idioma = "pt-BR";
    private boolean notificacoesEmail = true;
    private boolean notificacoesWhatsapp;
    @Column(nullable = false) private OffsetDateTime criadoEm;
}
