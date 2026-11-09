package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.AtividadeDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.CategoriaAtividade;
import com.hugabuga.api.service.AtividadeService;
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

@Tag(name = "Atividades", description = "Aventuras da tribo: catálogo, horários com vagas, agendamento e agenda do dia")
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class AtividadeController {

    private final AtividadeService atividadeService;

    // ---------- leitura pública ----------

    @Operation(summary = "Catálogo de atividades", description = "Com a contagem por categoria (chips Todas/Crianças/Família...).")
    @GetMapping("/atividades")
    public CatalogoRes catalogo(@RequestParam(required = false) CategoriaAtividade categoria) {
        return atividadeService.catalogo(categoria);
    }

    @Operation(summary = "Sessões do dia", description = "Todas as atividades de um dia com vagas (seletor de dias). Padrão: hoje.")
    @GetMapping("/atividades/sessoes")
    public List<SessaoDoDiaRes> sessoesDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(required = false) CategoriaAtividade categoria) {
        return atividadeService.sessoesDoDia(data, categoria);
    }

    @Operation(summary = "Detalhe da atividade")
    @GetMapping("/atividades/{id}")
    public AtividadeRes detalhe(@PathVariable UUID id) {
        return atividadeService.detalhe(id);
    }

    @Operation(summary = "Horários e vagas de uma atividade", description = "Padrão: de hoje a 7 dias.")
    @GetMapping("/atividades/{id}/sessoes")
    public List<SessaoRes> sessoes(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return atividadeService.sessoes(id, de, ate);
    }

    @Operation(summary = "Botão \"Avisar\"", description = "Registra o pedido de aviso de vaga (caverna ou atividade).")
    @PostMapping("/alertas-vaga")
    @ResponseStatus(HttpStatus.CREATED)
    public void alerta(@Valid @RequestBody AlertaReq req, @AuthenticationPrincipal UUID usuarioId) {
        atividadeService.registrarAlerta(req, usuarioId);
    }

    // ---------- dono da reserva ----------

    @Operation(summary = "Agenda uma sessão", description = "Reserva CONFIRMADA, sessão dentro da estadia e faixa de idade respeitada. 409 VAGAS_ESGOTADAS.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @PostMapping("/reservas/{codigo}/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoRes agendar(@PathVariable String codigo, @Valid @RequestBody AgendarReq req, Solicitante s) {
        return atividadeService.agendar(codigo, req, s);
    }

    @Operation(summary = "Cancela um agendamento", description = "Até 4 h antes; devolve a vaga.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @DeleteMapping("/reservas/{codigo}/agendamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable String codigo, @PathVariable UUID id, Solicitante s) {
        atividadeService.cancelar(codigo, id, s);
    }

    @Operation(summary = "Agenda da tribo no dia", description = "Café, atividades e mesa do Banquete. Sem data, usa o dia do check-in.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @GetMapping("/reservas/{codigo}/agenda")
    public AgendaRes agenda(@PathVariable String codigo,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                            Solicitante s) {
        return atividadeService.agenda(codigo, data, s);
    }
}
