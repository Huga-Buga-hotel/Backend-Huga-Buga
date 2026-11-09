package com.hugabuga.api.dto;

import com.hugabuga.api.model.CategoriaCaverna;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CavernaDtos {
    private CavernaDtos() {}

    /** Parâmetros da busca (o controller aplica os padrões de page/size). */
    public record BuscaFiltro(LocalDate checkIn, LocalDate checkOut, Integer adultos, List<Integer> idadesCriancas,
                              List<CategoriaCaverna> categoria, Integer precoMin, Integer precoMax,
                              List<String> comodidades, Boolean acessivel, String ordem, int page, int size) {}

    public record CavernaCardRes(UUID id, String slug, String nome, CategoriaCaverna categoria, String ala,
                                 String resumo, int capacidade, int camas, int areaM2, int precoBaseCentavos,
                                 Integer totalEstadiaCentavos, double notaMedia, int totalRelatos,
                                 boolean acessivel, boolean aceitaPets, String fotoUrl,
                                 Integer unidadesLivres, boolean ultimas, List<String> comodidades) {}

    public record SugestaoData(LocalDate checkIn, LocalDate checkOut, String rotulo) {}

    public record BuscaRes(long total, List<String> filtrosAtivos, List<String> avisos,
                           List<CavernaCardRes> itens, List<SugestaoData> sugestoes) {}

    public record NoiteValor(LocalDate data, int precoNoiteCentavos, int adicionalAdultosCentavos) {}

    public record CotacaoRes(int noites, int adultosCobrados, int adultosExtras, int criancasGratis,
                             int valorDiariasCentavos, List<NoiteValor> detalhe, boolean feriado) {}

    public record FotoRes(String url, String legenda) {}

    public record ComodidadeRes(String id, String nome, String icone) {}

    public record CavernaDetalheRes(CavernaCardRes caverna, String descricao, List<FotoRes> fotos,
                                    List<ComodidadeRes> comodidades) {}

    @Schema(description = "Avaliação (relato) de uma estadia: nota de 1 a 5, comentário e fotos.")
    public record RelatoRes(UUID id, int nota, String texto, String autor, OffsetDateTime criadoEm,
                            @Schema(description = "URLs relativas das fotos (GET /api/v1/arquivos/relatos/{arquivo}).")
                            List<String> fotos) {}

    @Schema(description = "Uma noite do calendário de disponibilidade.")
    public record DiaDisponibilidadeRes(LocalDate data, int unidadesLivres, int totalUnidades, boolean disponivel,
                                        int precoNoiteCentavos, boolean feriado) {}

    public record OpcaoContagemRes(String valor, long quantidade) {}

    @Schema(description = "Tudo o que a barra de filtros precisa, mais as regras de tempo de estadia.")
    public record FiltrosRes(List<OpcaoContagemRes> categorias, int precoMinCentavos, int precoMaxCentavos,
                             List<ComodidadeRes> comodidades, int capacidadeMaxima, List<String> ordens,
                             int minNoites, int maxNoites, int antecedenciaMaxDias,
                             String horarioCheckIn, String horarioCheckOut) {}

    public record PaginaRes<T>(List<T> itens, int page, int size, long total) {}
}
