package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Imagem anexada a uma avaliação. O arquivo fica no disco; aqui só o nome gerado pela API. */
@Entity
@Table(name = "foto_relato")
@Getter @Setter
public class FotoRelato {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID relatoId;
    /** <uuid>.<ext>: nunca o nome enviado pelo cliente. */
    @Column(nullable = false, unique = true) private String arquivo;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private long tamanhoBytes;
    private int ordem;
}
