package com.hugabuga.api.config;

import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Lê "Authorization: Bearer <token>", valida e identifica o usuário (o principal é o UUID dele,
 * acessível nos controllers com @AuthenticationPrincipal UUID usuarioId).
 *
 * - Sem token: segue anônimo. Rota protegida responde 401 NAO_AUTENTICADO (entry point do SecurityConfig).
 * - Token vencido: 401 SESSAO_EXPIRADA. Token inválido: 401 NAO_AUTENTICADO. Vale também em rotas livres,
 *   para o front saber que precisa renovar a sessão.
 *
 * Não é um @Component de propósito: o SecurityConfig o instancia, para ele não ser registrado duas vezes.
 * O token nunca é escrito no log.
 */
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwt;
    private final String prefixoApi;

    public JwtFilter(JwtService jwt, String prefixoApi) {
        this.jwt = jwt;
        this.prefixoApi = prefixoApi;
    }

    /** Cadastro, login e refresh não olham o token: um token velho no header não pode atrapalhar o login. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // preflight do CORS
        String p = request.getRequestURI();
        return p.equals(prefixoApi + "/auth/cadastro")
                || p.equals(prefixoApi + "/auth/login")
                || p.equals(prefixoApi + "/auth/refresh");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        try {
            UUID usuarioId = jwt.validarEExtrairUsuario(header.substring(BEARER.length()).trim());
            var auth = new UsernamePasswordAuthenticationToken(usuarioId, null,
                    List.of(new SimpleGrantedAuthority("ROLE_HOSPEDE")));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ApiException e) {
            SecurityContextHolder.clearContext();
            RespostaErro.escrever(response, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
