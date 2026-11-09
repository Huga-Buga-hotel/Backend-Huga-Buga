package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.CavernaDtos.*;
import com.hugabuga.api.model.CategoriaCaverna;
import com.hugabuga.api.service.CavernaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Leitura pública: busca, filtros, detalhe, disponibilidade, cotação, avaliações e comodidades. */
@Tag(name = "Cavernas", description = "Suítes (cavernas), busca com filtros, calendário de disponibilidade e cotação da estadia")
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class CavernaController {

    private final CavernaService cavernaService;

    @Operation(summary = "Busca cavernas", description = """
            Sem datas: lista o catálogo. Com checkIn e checkOut: só cavernas com toca livre, com o total da estadia,
            quantas tocas restam e o selo "Últimas" (2 ou menos). Se nada estiver livre, 'sugestoes' traz datas próximas.""")
    @GetMapping("/cavernas")
    public BuscaRes buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer adultos,
            @Parameter(description = "Idade de cada criança, ex.: idadesCriancas=9&idadesCriancas=4")
            @RequestParam(required = false) List<Integer> idadesCriancas,
            @RequestParam(required = false) List<CategoriaCaverna> categoria,
            @Parameter(description = "Preço base mínimo, em centavos") @RequestParam(required = false) Integer precoMin,
            @Parameter(description = "Preço base máximo, em centavos") @RequestParam(required = false) Integer precoMax,
            @Parameter(description = "Ids de /comodidades (todas precisam existir na caverna)")
            @RequestParam(required = false) List<String> comodidades,
            @RequestParam(required = false) Boolean acessivel,
            @Parameter(schema = @Schema(allowableValues = {"nota", "menor_preco", "maior_preco"}))
            @RequestParam(required = false) String ordem,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return cavernaService.buscar(new BuscaFiltro(checkIn, checkOut, adultos, idadesCriancas, categoria,
                precoMin, precoMax, comodidades, acessivel, ordem, page, size));
    }

    @Operation(summary = "Opções da barra de filtros", description = """
            Categorias com contagem, faixa de preço, comodidades, capacidade máxima, ordenações e as regras de
            tempo de estadia (mínimo/máximo de noites, antecedência, horários de check-in e check-out).""")
    @GetMapping("/cavernas/filtros")
    public FiltrosRes filtros() {
        return cavernaService.filtros();
    }

    @Operation(summary = "Detalhe da caverna", description = "Fotos, descrição e comodidades.")
    @GetMapping("/cavernas/{slug}")
    public CavernaDetalheRes detalhe(@PathVariable String slug) {
        return cavernaService.detalhe(slug);
    }

    @Operation(summary = "Calendário de disponibilidade", description = """
            Para cada noite de 'de' a 'ate' (inclusive): tocas livres, preço da noite e se é feriado.
            Padrão: de hoje a 30 dias. Máximo de 92 dias por consulta.""")
    @GetMapping("/cavernas/{id}/disponibilidade")
    public List<DiaDisponibilidadeRes> disponibilidade(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return cavernaService.disponibilidade(id, de, ate);
    }

    @Operation(summary = "Cotação da estadia", description = """
            O quadro "R$ 520 × 3 noites". A diária cobre 2 adultos; cada adulto a mais soma R$ 60/noite;
            criança até 5 anos não paga; acima de 12 conta como adulto. Feriado usa a tarifa especial.""")
    @GetMapping("/cavernas/{id}/cotacao")
    public CotacaoRes cotacao(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "2") int adultos,
            @RequestParam(required = false) List<Integer> idadesCriancas) {
        return cavernaService.cotar(id, checkIn, checkOut, adultos, idadesCriancas);
    }

    @Operation(summary = "Avaliações da caverna", description = "Paginadas (10 por página), mais recentes primeiro, com as fotos.")
    @GetMapping("/cavernas/{id}/relatos")
    public PaginaRes<RelatoRes> relatos(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
        return cavernaService.relatos(id, page);
    }

    @Operation(summary = "Lista de comodidades", description = "Ids usados no filtro 'comodidades'.")
    @GetMapping("/comodidades")
    public List<ComodidadeRes> comodidades() {
        return cavernaService.comodidades();
    }
}
