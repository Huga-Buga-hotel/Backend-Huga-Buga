package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.CavernaDtos.CavernaCardRes;
import com.hugabuga.api.dto.ContaDtos.*;
import com.hugabuga.api.service.ContaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Tudo em /me exige login (o SecurityConfig barra quem não tem token). */
@RestController
@RequestMapping(SecurityConfig.API + "/me")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    // ---------- perfil ----------

    @GetMapping
    public PerfilRes perfil(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.perfil(usuarioId);
    }

    @PatchMapping
    public PerfilRes atualizar(@AuthenticationPrincipal UUID usuarioId, @RequestBody PerfilReq req) {
        return contaService.atualizar(usuarioId, req);
    }

    @PatchMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody SenhaReq req) {
        contaService.trocarSenha(usuarioId, req);
    }

    // ---------- tribo ----------

    @GetMapping("/membros")
    public List<MembroRes> membros(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.membros(usuarioId);
    }

    @PostMapping("/membros")
    @ResponseStatus(HttpStatus.CREATED)
    public MembroRes adicionarMembro(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody MembroReq req) {
        return contaService.adicionarMembro(usuarioId, req);
    }

    @PatchMapping("/membros/{id}")
    public MembroRes editarMembro(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id,
                                  @Valid @RequestBody MembroReq req) {
        return contaService.editarMembro(usuarioId, id, req);
    }

    @DeleteMapping("/membros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerMembro(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
        contaService.removerMembro(usuarioId, id);
    }

    // ---------- ossos ----------

    @GetMapping("/ossos")
    public OssosRes ossos(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.ossos(usuarioId);
    }

    // ---------- favoritos ----------

    @GetMapping("/favoritos")
    public List<CavernaCardRes> favoritos(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.favoritos(usuarioId);
    }

    @PostMapping("/favoritos/{cavernaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favoritar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID cavernaId) {
        contaService.favoritar(usuarioId, cavernaId);
    }

    @DeleteMapping("/favoritos/{cavernaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfavoritar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID cavernaId) {
        contaService.desfavoritar(usuarioId, cavernaId);
    }

    // ---------- minhas reservas ----------

    /** aba = proximas | passadas | canceladas (estadias e mesas). */
    @GetMapping("/reservas")
    public MinhasReservasRes minhasReservas(@AuthenticationPrincipal UUID usuarioId,
                                            @RequestParam(defaultValue = "proximas") String aba) {
        return contaService.minhasReservas(usuarioId, aba);
    }
}
