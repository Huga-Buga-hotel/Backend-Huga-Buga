package com.hugabuga.api.erro;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/** Formato padrão de erro de TODA resposta da API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResposta(int status,
                           String codigo,
                           String titulo,
                           String mensagem,
                           /** Código curto (ex.: HB-7Q2X), também gravado no log para rastrear o problema. */
                           String ref,
                           List<CampoErro> campos,
                           Map<String, Object> dados) {

    public record CampoErro(String campo, String erro) {}

    private static final SecureRandom RND = new SecureRandom();
    private static final String ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    public static String novaRef() {
        StringBuilder sb = new StringBuilder("HB-");
        for (int i = 0; i < 4; i++) sb.append(ALFABETO.charAt(RND.nextInt(ALFABETO.length())));
        return sb.toString();
    }
}
