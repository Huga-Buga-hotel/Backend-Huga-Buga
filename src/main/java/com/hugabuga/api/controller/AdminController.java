package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.AdminDtos.*;
import com.hugabuga.api.dto.CavernaDtos.PaginaRes;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.service.AdminService;
import com.hugabuga.api.service.RelatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Painel da equipe do hotel. Tudo exige role ADMIN (SecurityConfig). Para ver o voucher, cancelar ou fazer o
 * check-in de qualquer reserva, o admin usa as próprias rotas de /reservas com o token dele.
 */
@Tag(name = "Admin", description = "Painel da equipe do hotel (role ADMIN). O admin também pode agir em qualquer reserva pelas rotas de /reservas.")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@RestController
@RequestMapping(SecurityConfig.API + "/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final RelatoService relatoService;

    @Operation(summary = "Painel do dia", description = "Ocupação das tocas, entradas e saídas, receita do mês e lotação do Banquete. Padrão: hoje.")
    @GetMapping("/dashboard")
    public DashboardRes dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return adminService.dashboard(data);
    }

    // ---------- reservas e tocas ----------

    @Operation(summary = "Lista reservas", description = "Filtros opcionais: status, período (estadias que cruzam de..ate) e busca por código, e-mail ou nome.")
    @GetMapping("/reservas")
    public PaginaRes<ReservaAdminRes> reservas(
            @RequestParam(required = false) StatusReserva status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.reservas(status, de, ate, busca, page, size);
    }

    @Operation(summary = "Mapa das tocas em uma noite", description = "Cada uma das 25 tocas: LIVRE, OCUPADA (com a reserva) ou MANUTENCAO.")
    @GetMapping("/ocupacao")
    public List<OcupacaoUnidadeRes> ocupacao(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return adminService.ocupacao(data);
    }

    @Operation(summary = "Coloca ou tira uma toca de manutenção", description = "Toca inativa some da disponibilidade; reservas já feitas continuam valendo.")
    @PatchMapping("/unidades/{id}")
    public OcupacaoUnidadeRes alterarUnidade(@PathVariable UUID id, @Valid @RequestBody UnidadeReq req) {
        return adminService.alterarUnidade(id, req);
    }

    // ---------- cavernas ----------

    @Operation(summary = "Lista as 5 suítes", description = "Inclui as inativas, com o número de tocas de cada uma.")
    @GetMapping("/cavernas")
    public List<CavernaAdminRes> cavernas() {
        return adminService.cavernas();
    }

    @Operation(summary = "Edita uma suíte", description = "Nome, textos, preço base ou ativa. Reservas já feitas mantêm o preço congelado.")
    @PatchMapping("/cavernas/{id}")
    public CavernaAdminRes alterarCaverna(@PathVariable UUID id, @Valid @RequestBody CavernaAdminReq req) {
        return adminService.alterarCaverna(id, req);
    }

    // ---------- Banquete ----------

    @Operation(summary = "Mesas do dia", description = "Todas as mesas (hóspede, combo e visitante de fora) e a lotação do salão em cada horário.")
    @GetMapping("/restaurante/mesas")
    public MesasDoDiaRes mesas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return adminService.mesasDoDia(data);
    }

    @Operation(summary = "Marca que a mesa não compareceu", description = "Só depois do horário da mesa.")
    @PostMapping("/restaurante/mesas/{codigo}/nao-compareceu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void naoCompareceu(@PathVariable String codigo) {
        adminService.naoCompareceu(codigo);
    }

    // ---------- avaliações e usuários ----------

    @Operation(summary = "Remove uma avaliação (moderação)", description = "Apaga as fotos e recalcula a nota da caverna.")
    @DeleteMapping("/relatos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRelato(@PathVariable UUID id) {
        relatoService.excluir(id);
    }

    @Operation(summary = "Lista usuários", description = "Busca por nome ou e-mail.")
    @GetMapping("/usuarios")
    public PaginaRes<UsuarioAdminRes> usuarios(@RequestParam(required = false) String busca,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return adminService.usuarios(busca, page, size);
    }

    @Operation(summary = "Muda o role (VISITANTE ou ADMIN)", description = "Vale a partir do próximo login/refresh do usuário. Ninguém tira o próprio acesso de admin.")
    @PatchMapping("/usuarios/{id}/role")
    public UsuarioAdminRes alterarRole(@AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
                                       @Valid @RequestBody RoleReq req) {
        return adminService.alterarRole(adminId, id, req);
    }
}
