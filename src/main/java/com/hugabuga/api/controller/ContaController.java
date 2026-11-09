package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.CavernaDtos.CavernaCardRes;
import com.hugabuga.api.dto.ContaDtos.*;
import com.hugabuga.api.service.ContaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Tudo em /me exige login (o SecurityConfig barra quem não tem token). */
@Tag(name = "Minha conta", description = "Perfil, preferências, membros da tribo, favoritos, pagamentos e Minhas reservas")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@RestController
@RequestMapping(SecurityConfig.API + "/me")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    // ---------- perfil ----------

    @Operation(summary = "Dados pessoais", description = "Documento sai mascarado (só os 4 últimos).")
    @GetMapping
    public PerfilRes perfil(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.perfil(usuarioId);
    }

    @Operation(summary = "Edita dados pessoais", description = "Campos nulos não são alterados.")
    @PatchMapping
    public PerfilRes atualizar(@AuthenticationPrincipal UUID usuarioId, @RequestBody PerfilReq req) {
        return contaService.atualizar(usuarioId, req);
    }

    @Operation(summary = "Troca a senha", description = "Derruba todas as sessões abertas.")
    @PatchMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody SenhaReq req) {
        contaService.trocarSenha(usuarioId, req);
    }

    @Operation(summary = "Idioma e notificações")
    @GetMapping("/preferencias")
    public PreferenciasRes preferencias(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.preferencias(usuarioId);
    }

    @Operation(summary = "Altera idioma e notificações", description = "Campos nulos não são alterados.")
    @PutMapping("/preferencias")
    public PreferenciasRes atualizarPreferencias(@AuthenticationPrincipal UUID usuarioId,
                                                 @Valid @RequestBody PreferenciasReq req) {
        return contaService.atualizarPreferencias(usuarioId, req);
    }

    // ---------- tribo ----------

    @Operation(summary = "Membros da tribo", description = "Usados para preencher \"Quem vem pra caverna?\" no checkout.")
    @GetMapping("/membros")
    public List<MembroRes> membros(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.membros(usuarioId);
    }

    @Operation(summary = "Adiciona membro da tribo", description = "Criança exige data de nascimento.")
    @PostMapping("/membros")
    @ResponseStatus(HttpStatus.CREATED)
    public MembroRes adicionarMembro(@AuthenticationPrincipal UUID usuarioId, @Valid @RequestBody MembroReq req) {
        return contaService.adicionarMembro(usuarioId, req);
    }

    @Operation(summary = "Edita membro da tribo")
    @PatchMapping("/membros/{id}")
    public MembroRes editarMembro(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id,
                                  @Valid @RequestBody MembroReq req) {
        return contaService.editarMembro(usuarioId, id, req);
    }

    @Operation(summary = "Remove membro da tribo")
    @DeleteMapping("/membros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerMembro(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
        contaService.removerMembro(usuarioId, id);
    }

    // ---------- favoritos ----------

    @Operation(summary = "Cavernas favoritas")
    @GetMapping("/favoritos")
    public List<CavernaCardRes> favoritos(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.favoritos(usuarioId);
    }

    @Operation(summary = "Favorita uma caverna (coração)", description = "Idempotente.")
    @PostMapping("/favoritos/{cavernaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favoritar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID cavernaId) {
        contaService.favoritar(usuarioId, cavernaId);
    }

    @Operation(summary = "Desfavorita uma caverna")
    @DeleteMapping("/favoritos/{cavernaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfavoritar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID cavernaId) {
        contaService.desfavoritar(usuarioId, cavernaId);
    }

    // ---------- pagamentos e reservas ----------

    @Operation(summary = "Histórico de pagamentos", description = "Nenhum cartão fica salvo: só final e bandeira.")
    @GetMapping("/pagamentos")
    public List<PagamentoResumoRes> pagamentos(@AuthenticationPrincipal UUID usuarioId) {
        return contaService.pagamentos(usuarioId);
    }

    @Operation(summary = "Minhas reservas", description = "Estadias e mesas da aba escolhida, com a contagem de cada aba.")
    @GetMapping("/reservas")
    public MinhasReservasRes minhasReservas(
            @AuthenticationPrincipal UUID usuarioId,
            @Parameter(schema = @Schema(allowableValues = {"proximas", "passadas", "canceladas"}))
            @RequestParam(defaultValue = "proximas") String aba) {
        return contaService.minhasReservas(usuarioId, aba);
    }
}
