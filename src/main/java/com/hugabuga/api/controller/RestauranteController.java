package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.ReservaDtos.ComboRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.dto.RestauranteDtos.*;
import com.hugabuga.api.model.CategoriaCardapio;
import com.hugabuga.api.service.ReservaService;
import com.hugabuga.api.service.RestauranteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** O Banquete. Leitura é livre; reservar mesa vale para hóspede e para visitante de fora. */
@Tag(name = "O Banquete", description = "Restaurante: turnos, cardápio, horários (salão de 50 lugares), combos e reserva de mesa")
@RestController
@RequestMapping(SecurityConfig.API + "/restaurante")
@RequiredArgsConstructor
public class RestauranteController {

    private final RestauranteService restauranteService;
    private final ReservaService reservaService;

    @Operation(summary = "Visão geral", description = "Turnos, \"aberto agora\", desconto de hóspede e capacidade do salão.")
    @GetMapping
    public RestauranteRes visaoGeral() {
        return restauranteService.visaoGeral();
    }

    @Operation(summary = "Cardápio", description = "Filtro opcional pela aba (Na fogueira, Entradas, Menu kids...).")
    @GetMapping("/cardapio")
    public List<ItemCardapioRes> cardapio(@RequestParam(required = false) CategoriaCardapio categoria) {
        return restauranteService.cardapio(categoria);
    }

    @Operation(summary = "Combos quarto + Banquete", description = "Escolhidos no checkout: PUT /reservas/{codigo}/combo.")
    @GetMapping("/combos")
    public List<ComboRes> combos() {
        return reservaService.listarCombos();
    }

    @Operation(summary = "Horários LIVRE ou LOTADO", description = """
            Para a data e o número de pessoas. Uma mesa ocupa o salão por 90 min, então mesas de horários
            vizinhos somam juntas na capacidade de 50 lugares.""")
    @GetMapping("/horarios")
    public List<HorarioRes> horarios(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                     @RequestParam(defaultValue = "2") int pessoas) {
        return restauranteService.horarios(data, pessoas);
    }

    @Operation(summary = "Reserva mesa", description = """
            Hóspede: informe 'codigoReserva' (estadia CONFIRMADA, data dentro dela; ganha 15% de desconto).
            Visitante de fora: sem 'codigoReserva', com nome, e-mail e celular (ou logado), até 60 dias à frente.
            409 VAGAS_ESGOTADAS se o salão lotar.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @PostMapping("/reservas-mesa")
    @ResponseStatus(HttpStatus.CREATED)
    public MesaRes reservarMesa(@Valid @RequestBody MesaReq req, Solicitante s) {
        return restauranteService.reservarMesa(req, s);
    }

    @Operation(summary = "Detalhe da mesa", description = "Dono da estadia, conta que reservou ou X-Responsavel-Email com o e-mail de contato.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @GetMapping("/reservas-mesa/{codigo}")
    public MesaRes detalhe(@PathVariable String codigo, Solicitante s) {
        return restauranteService.detalhe(codigo, s);
    }

    @Operation(summary = "Cancela a mesa", description = "Até 2 h antes. Mesas do combo saem só alterando/cancelando a estadia.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @SecurityRequirement(name = OpenApiConfig.EMAIL)
    @DeleteMapping("/reservas-mesa/{codigo}")
    public MesaRes cancelar(@PathVariable String codigo, Solicitante s) {
        return restauranteService.cancelar(codigo, s);
    }
}
