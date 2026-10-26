package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "caverna")
@Getter @Setter
public class Caverna {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true) private String slug;
    @Column(nullable = false) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CategoriaCaverna categoria;
    private String ala;
    private String resumo;
    @Column(columnDefinition = "text") private String descricao;
    @Column(nullable = false) private int capacidade;
    private int camas;
    private int areaM2;
    @Column(nullable = false) private int precoBaseCentavos;
    private boolean acessivel;
    private boolean aceitaPets;
    /** Recalculada a cada novo relato. No banco: double precision. */
    private double notaMedia;
    private int totalRelatos;
    private boolean ativa = true;

    /** Tabela de ligação caverna_comodidade (sem classe própria). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "caverna_comodidade",
            joinColumns = @JoinColumn(name = "caverna_id"),
            inverseJoinColumns = @JoinColumn(name = "comodidade_id"))
    private Set<Comodidade> comodidades = new HashSet<>();
}
