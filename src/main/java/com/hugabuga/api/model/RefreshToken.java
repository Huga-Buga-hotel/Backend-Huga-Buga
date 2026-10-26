package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "refresh_token")
@Getter @Setter
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID usuarioId;
    /** Só o hash SHA-256 é guardado. */
    @Column(nullable = false, unique = true) private String tokenHash;
    @Column(nullable = false) private OffsetDateTime expiraEm;
    private boolean revogado;
}
