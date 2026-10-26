package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.AtividadeDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.CategoriaAtividade;
import com.hugabuga.api.service.AtividadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class AtividadeController {

    private final AtividadeService atividadeService;

    // ---------- leitura pública ----------

    @GetMapping("/atividades")
    public CatalogoRes catalogo(@RequestParam(required = false) CategoriaAtividade categoria) {
        return atividadeService.catalogo(categoria);
    }

    @GetMapping("/atividades/{id}")
    public AtividadeRes detalhe(@PathVariable UUID id) {
        return atividadeService.detalhe(id);
    }

    /** Horários com vagasTotais, vagasLivres e esgotada. Padrão: de hoje a 7 dias. */
    @GetMapping("/atividades/{id}/sessoes")
    public List<SessaoRes> sessoes(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return atividadeService.sessoes(id, de, ate);
    }

    /** Botão "Avisar" (opcional: só grava o pedido). */
    @PostMapping("/alertas-vaga")
    @ResponseStatus(HttpStatus.CREATED)
    public void alerta(@Valid @RequestBody AlertaReq req, @AuthenticationPrincipal UUID usuarioId) {
        atividadeService.registrarAlerta(req, usuarioId);
    }

    // ---------- dono da reserva ----------

    @PostMapping("/reservas/{codigo}/agendamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AgendamentoRes agendar(@PathVariable String codigo, @Valid @RequestBody AgendarReq req, Solicitante s) {
        return atividadeService.agendar(codigo, req, s);
    }

    /** Cancela até 4 h antes e devolve a vaga. */
    @DeleteMapping("/reservas/{codigo}/agendamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable String codigo, @PathVariable UUID id, Solicitante s) {
        atividadeService.cancelar(codigo, id, s);
    }

    /** Agenda da tribo no dia (café, atividades e mesa). Sem data, usa o dia do check-in. */
    @GetMapping("/reservas/{codigo}/agenda")
    public AgendaRes agenda(@PathVariable String codigo,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                            Solicitante s) {
        return atividadeService.agenda(codigo, data, s);
    }
}
