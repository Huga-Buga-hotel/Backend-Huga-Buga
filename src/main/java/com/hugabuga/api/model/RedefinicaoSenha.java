package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Pedido de "esqueci a senha". Token de uso único; só o hash SHA-256 é guardado. */
@Entity
@Table(name = "redefinicao_senha")
@Getter @Setter
public class RedefinicaoSenha {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID usuarioId;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Column(nullable = false) private OffsetDateTime expiraEm;
    private boolean usado;
}
