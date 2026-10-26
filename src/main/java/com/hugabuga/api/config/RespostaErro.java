package com.hugabuga.api.config;

import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.erro.ErroResposta;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve o erro padrão (mesmo formato do GlobalExceptionHandler) em respostas geradas FORA do MVC,
 * como o JwtFilter e os handlers de segurança, onde o @RestControllerAdvice não alcança.
 * O JSON é montado à mão para não depender de qual versão do Jackson está no classpath.
 */
@Slf4j
final class RespostaErro {

    private RespostaErro() {}

    static void escrever(HttpServletResponse res, ApiException e) throws IOException {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] {} {} - {}", ref, e.getStatus().value(), e.getCodigo(), e.getMessage());

        String json = "{\"status\":" + e.getStatus().value()
                + ",\"codigo\":\"" + esc(e.getCodigo()) + "\""
                + ",\"titulo\":\"" + esc(e.getTitulo()) + "\""
                + ",\"mensagem\":\"" + esc(e.getMessage()) + "\""
                + ",\"ref\":\"" + ref + "\"}";

        res.setStatus(e.getStatus().value());
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(json);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }
}
