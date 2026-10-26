package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "item_cardapio")
@Getter @Setter
public class ItemCardapio {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private String nome;
    private String descricao;
    @Column(nullable = false) private int precoCentavos;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CategoriaCardapio categoria;
    /** Carro-chefe, Sem glúten, Vegano, Kids, Pra dividir. Coluna text[] do PostgreSQL. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();
    private int ordem;
    private boolean ativo = true;
}
