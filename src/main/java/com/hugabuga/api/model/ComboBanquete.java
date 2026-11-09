package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Combo quarto + Banquete: jantar todas as noites da estadia. Id textual (ex.: JANTAR_DA_TRIBO). */
@Entity
@Table(name = "combo_banquete")
@Getter @Setter
public class ComboBanquete {
    @Id private String id;
    @Column(nullable = false) private String nome;
    private String descricao;
    /** Por pessoa, por noite. Quem tem mais de 12 anos paga como adulto. */
    @Column(nullable = false) private int precoAdultoCentavos;
    /** Por criança de 6 a 12 anos, por noite. Até 5 anos não paga. */
    @Column(nullable = false) private int precoCriancaCentavos;
    private boolean ativo = true;
}
