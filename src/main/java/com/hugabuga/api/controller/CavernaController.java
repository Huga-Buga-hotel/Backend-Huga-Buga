package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.CavernaDtos.*;
import com.hugabuga.api.model.CategoriaCaverna;
import com.hugabuga.api.service.CavernaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Leitura pública: busca, detalhe, cotação, relatos e comodidades. */
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class CavernaController {

    private final CavernaService cavernaService;

    @GetMapping("/cavernas")
    public BuscaRes buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer adultos,
            @RequestParam(required = false) List<Integer> idadesCriancas,
            @RequestParam(required = false) List<CategoriaCaverna> categoria,
            @RequestParam(required = false) Integer precoMin,
            @RequestParam(required = false) Integer precoMax,
            @RequestParam(required = false) List<String> comodidades,
            @RequestParam(required = false) Boolean acessivel,
            @RequestParam(required = false) String ordem,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return cavernaService.buscar(new BuscaFiltro(checkIn, checkOut, adultos, idadesCriancas, categoria,
                precoMin, precoMax, comodidades, acessivel, ordem, page, size));
    }

    @GetMapping("/cavernas/{slug}")
    public CavernaDetalheRes detalhe(@PathVariable String slug) {
        return cavernaService.detalhe(slug);
    }

    /** O quadro "R$ 520 × 3 noites". */
    @GetMapping("/cavernas/{id}/cotacao")
    public CotacaoRes cotacao(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "2") int adultos,
            @RequestParam(required = false) List<Integer> idadesCriancas) {
        return cavernaService.cotar(id, checkIn, checkOut, adultos, idadesCriancas);
    }

    @GetMapping("/cavernas/{id}/relatos")
    public PaginaRes<RelatoRes> relatos(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
        return cavernaService.relatos(id, page);
    }

    @GetMapping("/comodidades")
    public List<ComodidadeRes> comodidades() {
        return cavernaService.comodidades();
    }
}
