package com.hugabuga.api.model;

/** Níveis por ossos ACUMULADOS (nunca caem). */
public enum NivelFidelidade {
    COLETOR(0, "Coletor"),
    CACADOR(200, "Caçador"),
    XAMA(500, "Xamã"),
    CHEFE_DA_TRIBO(1000, "Chefe da Tribo");

    private final long minimo;
    private final String nome;

    NivelFidelidade(long minimo, String nome) { this.minimo = minimo; this.nome = nome; }

    public long getMinimo() { return minimo; }
    public String getNome() { return nome; }

    public static NivelFidelidade de(long acumulado) {
        NivelFidelidade r = COLETOR;
        for (NivelFidelidade n : values()) if (acumulado >= n.minimo) r = n;
        return r;
    }

    /** Próximo nível, ou null se já é o último. */
    public NivelFidelidade proximo() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }
}
