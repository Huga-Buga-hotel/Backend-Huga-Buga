package com.hugabuga.api.service;

import com.hugabuga.api.dto.AuthDtos.*;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.RefreshToken;
import com.hugabuga.api.model.Usuario;
import com.hugabuga.api.repository.RefreshTokenRepository;
import com.hugabuga.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Cadastro, login, refresh e logout. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int SENHA_MINIMA = 8;

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    @Transactional
    public TokensRes cadastrar(CadastroReq req) {
        validarSenha(req.senha());
        String email = req.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDACAO", "Uga! Esse e-mail já tem toca.",
                    "Já existe uma conta com este e-mail. Nenhuma conta nova foi criada. Entre na sua conta ou use outro e-mail.",
                    Map.of("campos", List.of(Map.of("campo", "email", "erro", "E-mail já cadastrado"))));
        }
        Usuario u = new Usuario();
        u.setNome(req.nome().trim());
        u.setSobrenome(req.sobrenome().trim());
        u.setEmail(email);
        u.setSenhaHash(encoder.encode(req.senha()));
        u.setCelular(req.celular());
        u.setPais(req.pais().toUpperCase());
        u.setTipoDocumento(req.tipoDocumento().toUpperCase());
        u.setNumeroDocumento(req.numeroDocumento());
        u.setDataNascimento(req.dataNascimento());
        u.setAceitaOfertas(req.aceitaOfertas());
        u.setSaldoOssos(0);
        u.setCriadoEm(Util.agora());
        u = usuarios.save(u);
        return emitir(u, false);
    }

    @Transactional
    public TokensRes login(LoginReq req) {
        Usuario u = usuarios.findByEmailIgnoreCase(req.email().trim()).orElse(null);
        // Mesma resposta para e-mail inexistente e senha errada: não revela se a conta existe.
        if (u == null || !encoder.matches(req.senha(), u.getSenhaHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Uga! Não reconhecemos você.",
                    "E-mail ou senha incorretos. Você não foi conectado. Confira os dados e tente de novo.");
        }
        return emitir(u, req.lembrar());
    }

    @Transactional(readOnly = true)
    public TokensRes refresh(RefreshReq req) {
        RefreshToken rt = refreshTokens.findByTokenHash(jwt.hash(req.refreshToken())).orElseThrow(this::sessaoExpirada);
        if (rt.isRevogado() || rt.getExpiraEm().isBefore(Util.agora())) throw sessaoExpirada();
        Usuario u = usuarios.findById(rt.getUsuarioId()).orElseThrow(this::sessaoExpirada);
        return new TokensRes(jwt.gerarAcesso(u), req.refreshToken(), jwt.segundosAcesso(), u.getId(), u.getNome());
    }

    @Transactional
    public void logout(java.util.UUID usuarioId, RefreshReq req) {
        refreshTokens.findByTokenHash(jwt.hash(req.refreshToken()))
                .filter(rt -> rt.getUsuarioId().equals(usuarioId))
                .ifPresent(rt -> { rt.setRevogado(true); refreshTokens.save(rt); });
    }

    /** Usado na troca de senha: derruba todas as sessões ativas. */
    @Transactional
    public void revogarTodos(java.util.UUID usuarioId) {
        List<RefreshToken> ativos = refreshTokens.findByUsuarioIdAndRevogadoFalse(usuarioId);
        ativos.forEach(rt -> rt.setRevogado(true));
        refreshTokens.saveAll(ativos);
    }

    // ---------- internos ----------

    private TokensRes emitir(Usuario u, boolean lembrar) {
        String refresh = jwt.novoRefresh();
        RefreshToken rt = new RefreshToken();
        rt.setUsuarioId(u.getId());
        rt.setTokenHash(jwt.hash(refresh)); // só o hash vai para o banco
        rt.setExpiraEm(Util.agora().plus(jwt.validadeRefresh(lembrar)));
        rt.setRevogado(false);
        refreshTokens.save(rt);
        return new TokensRes(jwt.gerarAcesso(u), refresh, jwt.segundosAcesso(), u.getId(), u.getNome());
    }

    static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw Util.validacao("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
    }

    private ApiException sessaoExpirada() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSAO_EXPIRADA", "Uga! Sua sessão acabou.",
                "Sua sessão expirou. Nada foi alterado. Entre novamente para continuar.");
    }
}
