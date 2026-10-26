package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.AuthDtos.*;
import com.hugabuga.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Só recebe, valida e delega. Nenhuma regra de negócio aqui. */
@RestController
@RequestMapping(SecurityConfig.API + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokensRes cadastro(@Valid @RequestBody CadastroReq req) {
        return authService.cadastrar(req);
    }

    @PostMapping("/login")
    public TokensRes login(@Valid @RequestBody LoginReq req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public TokensRes refresh(@Valid @RequestBody RefreshReq req) {
        return authService.refresh(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody RefreshReq req) {
        authService.logout(usuarioId, req);
    }
}
