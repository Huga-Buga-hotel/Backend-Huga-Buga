package com.hugabuga.api.service;

import com.hugabuga.api.erro.ApiException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Utilitários compartilhados pelos services: fuso fixo, códigos, dinheiro e erros padrão. */
public final class Util {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final SecureRandom RND = new SecureRandom();
    // sem 0/O, 1/I para evitar confusão ao ditar o código
    private static final String ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private Util() {}

    public static OffsetDateTime agora() { return OffsetDateTime.now(FUSO); }

    public static LocalDate hoje() { return LocalDate.now(FUSO); }

    /** Gera códigos curtos como HB-4F7K ou BQ-9XQ2. A unicidade é conferida por quem chama. */
    public static String codigo(String prefixo) {
        StringBuilder sb = new StringBuilder(prefixo).append('-');
        for (int i = 0; i < 4; i++) sb.append(ALFABETO.charAt(RND.nextInt(ALFABETO.length())));
        return sb.toString();
    }

    /** Percentual sobre centavos, arredondando meio para cima, só com inteiros. */
    public static int porcentagem(long baseCentavos, int pct) {
        return (int) ((baseCentavos * pct + 50) / 100);
    }

    /** Só para mensagens de erro: 52000 -> "R$ 520,00". */
    public static String reais(int centavos) {
        return "R$ " + (centavos / 100) + "," + String.format("%02d", Math.abs(centavos % 100));
    }

    // ---------- erros padrão (o que aconteceu, o que NÃO aconteceu, próximo passo) ----------

    public static ApiException validacao(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDACAO", "Uga! Algo não bateu nos dados.",
                msg + " Nada foi salvo. Corrija e tente novamente.");
    }

    public static ApiException naoEncontrado(String o) {
        return new ApiException(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Uga! Não achamos isso.",
                o + " não foi encontrado(a). Nada foi alterado. Confira o código e tente de novo.");
    }

    public static ApiException semPermissao() {
        return new ApiException(HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Uga! Essa toca não é sua.",
                "Esta reserva pertence a outra pessoa. Nada foi alterado. Entre com a conta certa ou informe o e-mail do responsável.");
    }

    public static ApiException naoAutenticado() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Uga! Quem é você?",
                "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.");
    }

    public static ApiException estadoInvalido(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO", "Uga! Isso não dá agora.",
                msg + " Nada foi alterado.");
    }

    public static ApiException foraDoPrazo(String msg) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FORA_DO_PRAZO", "Uga! Fora do prazo.",
                msg + " Nada foi alterado.");
    }
}
