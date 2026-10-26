package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.dto.RestauranteDtos.*;
import com.hugabuga.api.model.CategoriaCardapio;
import com.hugabuga.api.service.RestauranteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** O Banquete. Leitura é livre; reservar, ver e cancelar mesa é para o dono da reserva. */
@RestController
@RequestMapping(SecurityConfig.API + "/restaurante")
@RequiredArgsConstructor
public class RestauranteController {

    private final RestauranteService restauranteService;

    /** Turnos, "aberto agora" e desconto de hóspede. */
    @GetMapping
    public RestauranteRes visaoGeral() {
        return restauranteService.visaoGeral();
    }

    @GetMapping("/cardapio")
    public List<ItemCardapioRes> cardapio(@RequestParam(required = false) CategoriaCardapio categoria) {
        return restauranteService.cardapio(categoria);
    }

    /** Cada horário LIVRE ou LOTADO para a data e o número de pessoas. */
    @GetMapping("/horarios")
    public List<HorarioRes> horarios(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                     @RequestParam(defaultValue = "2") int pessoas) {
        return restauranteService.horarios(data, pessoas);
    }

    /** Só com reserva CONFIRMADA cobrindo a data. */
    @PostMapping("/reservas-mesa")
    @ResponseStatus(HttpStatus.CREATED)
    public MesaRes reservarMesa(@Valid @RequestBody MesaReq req, Solicitante s) {
        return restauranteService.reservarMesa(req, s);
    }

    @GetMapping("/reservas-mesa/{codigo}")
    public MesaRes detalhe(@PathVariable String codigo, Solicitante s) {
        return restauranteService.detalhe(codigo, s);
    }

    /** Cancela até 2 h antes. */
    @DeleteMapping("/reservas-mesa/{codigo}")
    public MesaRes cancelar(@PathVariable String codigo, Solicitante s) {
        return restauranteService.cancelar(codigo, s);
    }
}
