package com.hugabuga.api.erro;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Única exceção lançada pelos services. Carrega tudo que o GlobalExceptionHandler precisa
 * para montar o formato padrão de erro.
 *
 * Boa prática de mensagem: dizer o que aconteceu, o que NÃO aconteceu e o próximo passo.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final String titulo;
    private final Map<String, Object> dados;

    public ApiException(HttpStatus status, String codigo, String titulo, String mensagem) {
        this(status, codigo, titulo, mensagem, null);
    }

    public ApiException(HttpStatus status, String codigo, String titulo, String mensagem, Map<String, Object> dados) {
        // sem stack trace: são erros de negócio esperados, não falhas de programação (mais barato e sem ruído no log)
        super(mensagem, null, false, false);
        this.status = status;
        this.codigo = codigo;
        this.titulo = titulo;
        this.dados = dados;
    }

    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
    public String getTitulo() { return titulo; }
    /** Dados extras para o front (datas sugeridas, preço antigo e novo...). Pode ser null. */
    public Map<String, Object> getDados() { return dados; }
}
