package com.hugabuga.api.erro;

import com.hugabuga.api.erro.ErroResposta.CampoErro;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Transforma qualquer exceção no formato padrão (ErroResposta).
 * Nunca expõe stack trace ao cliente; nunca registra corpo de requisição (pode ter cartão/senha).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------- erros de negócio (lançados pelos services) ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroResposta> negocio(ApiException e) {
        String ref = ErroResposta.novaRef();
        if (e.getStatus().is5xxServerError()) {
            log.error("[{}] {} {} - {}", ref, e.getStatus().value(), e.getCodigo(), e.getMessage());
        } else {
            log.warn("[{}] {} {} - {}", ref, e.getStatus().value(), e.getCodigo(), e.getMessage());
        }
        return resposta(e.getStatus(), e.getCodigo(), e.getTitulo(), e.getMessage(), ref, null, e.getDados());
    }

    // ---------- validação (400 VALIDACAO, listando cada campo) ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> corpoInvalido(MethodArgumentNotValidException e) {
        List<CampoErro> campos = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new CampoErro(f.getField(), f.getDefaultMessage() == null ? "valor inválido" : f.getDefaultMessage()))
                .toList();
        return validacao("Alguns campos estão inválidos.", campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResposta> parametrosInvalidos(ConstraintViolationException e) {
        List<CampoErro> campos = e.getConstraintViolations().stream().map(v -> {
            String caminho = v.getPropertyPath().toString();
            return new CampoErro(caminho.substring(caminho.lastIndexOf('.') + 1), v.getMessage());
        }).toList();
        return validacao("Alguns parâmetros estão inválidos.", campos);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tipoErrado(MethodArgumentTypeMismatchException e) {
        return validacao("O valor informado para '" + e.getName() + "' não é válido.",
                List.of(new CampoErro(e.getName(), "valor inválido")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResposta> faltouParametro(MissingServletRequestParameterException e) {
        return validacao("Falta o parâmetro '" + e.getParameterName() + "'.",
                List.of(new CampoErro(e.getParameterName(), "obrigatório")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> jsonIlegivel(HttpMessageNotReadableException e) {
        // JSON malformado, data fora do formato ISO, enum desconhecido...
        return validacao("Não conseguimos ler os dados enviados (confira o JSON e o formato das datas, ex.: 2026-10-12).", List.of());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErroResposta> parametrosDoMetodoInvalidos(HandlerMethodValidationException e) {
        List<CampoErro> campos = e.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(err -> new CampoErro(
                        r.getMethodParameter().getParameterName() == null ? "parametro" : r.getMethodParameter().getParameterName(),
                        err.getDefaultMessage() == null ? "valor inválido" : err.getDefaultMessage())))
                .toList();
        return validacao("Alguns parâmetros estão inválidos.", campos);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestPartException.class,
            MultipartException.class})
    public ResponseEntity<ErroResposta> requisicaoIncompleta(Exception e) {
        return validacao("A requisição veio incompleta (confira os campos, os arquivos e os headers).", List.of());
    }

    // ---------- upload grande: 413 ----------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResposta> arquivoGrande(MaxUploadSizeExceededException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 413 upload acima do limite", ref);
        return resposta(HttpStatus.CONTENT_TOO_LARGE, "ARQUIVO_GRANDE", "Uga! Essa pedra é pesada demais.",
                "Cada foto pode ter até 25 MB (no máximo 5 por avaliação). Nada foi salvo. Diminua as imagens e tente de novo.",
                ref, null, null);
    }

    // ---------- rota inexistente, método ou formato errado ----------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 404 rota inexistente: {}", ref, e.getMessage());
        return resposta(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Uga! Não achamos esse caminho.",
                "Essa rota não existe na API. Nada foi alterado. Confira o endereço e o método da chamada.", ref, null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodoNaoSuportado(HttpRequestMethodNotSupportedException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 405 método {} não suportado", ref, e.getMethod());
        return resposta(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO", "Uga! Esse caminho não aceita isso.",
                "A rota existe, mas não aceita o método " + e.getMethod() + ". Nada foi alterado. Confira a documentação em /api/v1/docs.",
                ref, null, null);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<ErroResposta> formatoNaoSuportado(Exception e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 415 formato não suportado: {}", ref, e.getMessage());
        return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FORMATO_NAO_SUPORTADO", "Uga! Não entendemos esse formato.",
                "Envie o corpo como application/json (ou multipart/form-data nas rotas com fotos). Nada foi alterado.",
                ref, null, null);
    }

    // ---------- conflito no banco (corrida que passou pelas validações) ----------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> conflitoNoBanco(DataIntegrityViolationException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 409 violação de integridade", ref, e);
        return resposta(HttpStatus.CONFLICT, "CONFLITO", "Ugh! Alguém chegou primeiro.",
                "Os dados mudaram enquanto você enviava e nada foi salvo. Atualize a tela e tente de novo.", ref, null, null);
    }

    // ---------- segurança (quando a exceção chega ao MVC; o JwtFilter trata o resto) ----------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResposta> naoAutenticado(AuthenticationException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 401 não autenticado", ref);
        return resposta(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Uga! Quem é você?",
                "Você precisa entrar na sua conta para fazer isso. Nada foi alterado. Faça login e tente de novo.", ref, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> semPermissao(AccessDeniedException e) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 403 sem permissão", ref);
        return resposta(HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Uga! Essa toca não é sua.",
                "Você não tem permissão para isso. Nada foi alterado. Entre com a conta certa e tente de novo.", ref, null, null);
    }

    // ---------- banco fora do ar e falhas inesperadas: 503 INDISPONIVEL ----------

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            JDBCConnectionException.class})
    public ResponseEntity<ErroResposta> bancoForaDoAr(Exception e) {
        return indisponivel(e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> inesperado(Exception e) {
        return indisponivel(e);
    }

    // ---------- internos ----------

    private ResponseEntity<ErroResposta> indisponivel(Exception e) {
        String ref = ErroResposta.novaRef();
        // O stack trace vai SÓ para o log, junto com a ref que o cliente recebe.
        log.error("[{}] 503 falha inesperada", ref, e);
        return resposta(HttpStatus.SERVICE_UNAVAILABLE, "INDISPONIVEL", "Ugh! A caverna desabou por um instante.",
                "Algo falhou do nosso lado e o seu pedido pode não ter sido concluído. Confira em Minhas reservas antes de repetir. Tente de novo em instantes; se persistir, informe o código " + ref + ".",
                ref, null, null);
    }

    private ResponseEntity<ErroResposta> validacao(String detalhe, List<CampoErro> campos) {
        String ref = ErroResposta.novaRef();
        log.warn("[{}] 400 VALIDACAO campos={}", ref, campos.stream().map(CampoErro::campo).toList());
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO", "Uga! Algo não bateu nos dados.",
                detalhe + " Nada foi salvo. Corrija e tente novamente.", ref, campos, null);
    }

    private ResponseEntity<ErroResposta> resposta(HttpStatus status, String codigo, String titulo, String mensagem,
                                                  String ref, List<CampoErro> campos, Map<String, Object> dados) {
        return ResponseEntity.status(status)
                .body(new ErroResposta(status.value(), codigo, titulo, mensagem, ref, campos, dados));
    }
}
