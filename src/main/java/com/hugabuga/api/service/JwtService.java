package com.hugabuga.api.service;

import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.Role;
import com.hugabuga.api.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/** Gera e valida o token de acesso (JWT) e o refresh token (opaco, guardado só como hash). */
@Service
public class JwtService {

    /** O que o token de acesso carrega: quem é e qual o papel. */
    public record Sessao(UUID usuarioId, Role role) {}

    private final SecretKey chave;
    private final long acessoMinutos;
    private final long refreshDias;
    private final SecureRandom random = new SecureRandom();

    public JwtService(@Value("${hugabuga.jwt.segredo}") String segredo,
                      @Value("${hugabuga.jwt.acesso-minutos:15}") long acessoMinutos,
                      @Value("${hugabuga.jwt.refresh-dias:30}") long refreshDias) {
        // O segredo vem de variável de ambiente e precisa ter ao menos 32 caracteres (HS256).
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.acessoMinutos = acessoMinutos;
        this.refreshDias = refreshDias;
    }

    public String gerarAcesso(Usuario u) {
        Instant agora = Instant.now();
        return Jwts.builder()
                .subject(u.getId().toString())
                .claim("email", u.getEmail())
                .claim("role", u.getRole().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(Duration.ofMinutes(acessoMinutos))))
                .signWith(chave)
                .compact();
    }

    /**
     * Valida o token e devolve o usuário e o papel. Vencido: SESSAO_EXPIRADA. Qualquer outro problema: NAO_AUTENTICADO.
     * Uma troca de papel feita pelo admin vale a partir do próximo token (no máximo 15 min).
     */
    public Sessao validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(chave).build().parseSignedClaims(token).getPayload();
            String role = c.get("role", String.class);
            return new Sessao(UUID.fromString(c.getSubject()), role == null ? Role.VISITANTE : Role.valueOf(role));
        } catch (ExpiredJwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSAO_EXPIRADA", "Uga! Sua sessão acabou.",
                    "Sua sessão expirou. Nada foi alterado. Entre novamente para continuar.");
        } catch (JwtException | IllegalArgumentException e) {
            throw Util.naoAutenticado();
        }
    }

    public long segundosAcesso() { return acessoMinutos * 60; }

    /** Refresh opaco: 32 bytes aleatórios. O valor em claro só vai para o cliente; no banco fica o hash. */
    public String novoRefresh() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public String hash(String valor) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Com "lembrar de mim": 30 dias. Sem: 1 dia. */
    public Duration validadeRefresh(boolean lembrar) {
        return lembrar ? Duration.ofDays(refreshDias) : Duration.ofDays(1);
    }
}
