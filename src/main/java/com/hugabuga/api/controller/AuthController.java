package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.AuthDtos.*;
import com.hugabuga.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Só recebe, valida e delega. Nenhuma regra de negócio aqui. */
@Tag(name = "Auth", description = "Cadastro, login, sessão e \"esqueci a senha\" (tela Entrar / Criar conta)")
@RestController
@RequestMapping(SecurityConfig.API + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Cria conta de visitante", description = "Já devolve os tokens (entra direto). Toda conta nova é VISITANTE.")
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokensRes cadastro(@Valid @RequestBody CadastroReq req) {
        return authService.cadastrar(req);
    }

    @Operation(summary = "Login", description = "Devolve accessToken (15 min), refreshToken e o role (VISITANTE ou ADMIN).")
    @PostMapping("/login")
    public TokensRes login(@Valid @RequestBody LoginReq req) {
        return authService.login(req);
    }

    @Operation(summary = "Renova o accessToken", description = "401 SESSAO_EXPIRADA se o refresh venceu ou foi revogado.")
    @PostMapping("/refresh")
    public TokensRes refresh(@Valid @RequestBody RefreshReq req) {
        return authService.refresh(req);
    }

    @Operation(summary = "Sair da conta", description = "Revoga o refresh token informado.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody RefreshReq req) {
        authService.logout(usuarioId, req);
    }

    @Operation(summary = "Esqueci a senha", description = """
            Gera um link de uso único (30 min). A resposta é sempre a mesma, exista ou não a conta.
            Sem servidor de e-mail: no perfil dev o token volta em 'tokenDev'.""")
    @PostMapping("/esqueci-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EsqueciSenhaRes esqueciSenha(@Valid @RequestBody EsqueciSenhaReq req) {
        return authService.esqueciSenha(req);
    }

    @Operation(summary = "Cria a nova senha com o token do link", description = "Derruba todas as sessões abertas da conta.")
    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaReq req) {
        authService.redefinirSenha(req);
    }
}
