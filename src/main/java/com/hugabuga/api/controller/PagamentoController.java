package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.PagamentoDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.service.PagamentoService;
import jakarta.validation.Valid;
import com.hugabuga.api.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Pagamento simulado. IMPORTANTE: o corpo de POST /reservas/{codigo}/pagamentos traz número de cartão e CVV.
 * Nunca registre o corpo desta requisição em log (nem com filtros de logging de requests).
 */
@Tag(name = "Pagamentos", description = "Pagamento simulado: cartão, Pix (5% de desconto) ou na chegada")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@SecurityRequirement(name = OpenApiConfig.EMAIL)
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoService pagamentoService;

    @Operation(summary = "Paga a reserva", description = """
            CARTAO: aprova na hora (1 a 6x, número válido no Luhn; finais 0002 e 0069 são recusados).
            PIX: devolve o copia e cola (10 min) e deixa a reserva AGUARDANDO_PAGAMENTO.
            NA_CHEGADA: confirma sem cobrar. Exige o passo 1 (hóspedes) feito.""")
    @PostMapping("/reservas/{codigo}/pagamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoRes pagar(@PathVariable String codigo, @Valid @RequestBody PagamentoReq req, Solicitante s) {
        return pagamentoService.pagar(codigo, req, s);
    }

    @Operation(summary = "Consulta o pagamento", description = "O front consulta a cada 3 s enquanto espera o Pix (no perfil dev ele aprova sozinho após 20 s).")
    @GetMapping("/pagamentos/{id}")
    public PagamentoRes consultar(@PathVariable UUID id, Solicitante s) {
        return pagamentoService.consultar(id, s);
    }

    @Operation(summary = "Confirma o Pix (simulação)", description = "Só no perfil dev; fora dele responde 404.")
    @PostMapping("/pagamentos/{id}/simular-confirmacao")
    public PagamentoRes simularConfirmacao(@PathVariable UUID id, Solicitante s) {
        return pagamentoService.simularConfirmacao(id, s);
    }
}
