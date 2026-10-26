package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Id textual legível (ex.: fogueira-privativa), usado no filtro. */
@Entity
@Table(name = "comodidade")
@Getter @Setter
public class Comodidade {
    @Id private String id;
    @Column(nullable = false) private String nome;
    /** Nome do ícone Material. */
    private String icone;
}
