package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.PagamentoDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.service.PagamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Pagamento simulado. IMPORTANTE: o corpo de POST /reservas/{codigo}/pagamentos traz número de cartão e CVV.
 * Nunca registre o corpo desta requisição em log (nem com filtros de logging de requests).
 */
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoService pagamentoService;

    /** Cartão, Pix ou Na chegada. */
    @PostMapping("/reservas/{codigo}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoRes pagar(@PathVariable String codigo, @Valid @RequestBody PagamentoReq req, Solicitante s) {
        return pagamentoService.pagar(codigo, req, s);
    }

    /** O front consulta a cada 3 s enquanto espera o Pix. */
    @GetMapping("/pagamentos/{id}")
    public PagamentoRes consultar(@PathVariable UUID id, Solicitante s) {
        return pagamentoService.consultar(id, s);
    }

    /** Só no perfil dev; fora dele responde 404. */
    @PostMapping("/pagamentos/{id}/simular-confirmacao")
    public PagamentoRes simularConfirmacao(@PathVariable UUID id, Solicitante s) {
        return pagamentoService.simularConfirmacao(id, s);
    }
}
