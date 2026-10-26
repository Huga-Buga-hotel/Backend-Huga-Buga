package com.hugabuga.api.dto;

import com.hugabuga.api.model.CategoriaCaverna;

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

    public record RelatoRes(int nota, String texto, String autor, OffsetDateTime criadoEm) {}

    public record PaginaRes<T>(List<T> itens, int page, int size, long total) {}
}
