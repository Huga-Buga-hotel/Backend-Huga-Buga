package com.hugabuga.api.dto;

import com.hugabuga.api.model.CategoriaAtividade;
import com.hugabuga.api.model.StatusAgendamento;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AtividadeDtos {
    private AtividadeDtos() {}

    public record AtividadeRes(UUID id, String nome, CategoriaAtividade categoria, String descricao,
                               int duracaoMin, Integer idadeMin, Integer idadeMax, int precoCentavos,
                               String cobranca, String destaque) {}

    public record CatalogoRes(List<AtividadeRes> itens, Map<String, Long> contagemPorCategoria) {}

    public record SessaoRes(UUID id, OffsetDateTime inicio, int vagasTotais, int vagasLivres, boolean esgotada) {}

    public record AgendarReq(@NotNull UUID sessaoId, @Min(1) int quantidade) {}

    public record AgendamentoRes(UUID id, UUID sessaoId, String atividade, OffsetDateTime inicio, int quantidade,
                                 int valorCentavos, StatusAgendamento status) {}

    public record AgendaItemRes(String hora, String tipo, String titulo, String detalhe) {}

    public record AgendaRes(LocalDate data, List<AgendaItemRes> itens) {}

    public record AlertaReq(@NotBlank @Email String email, @NotBlank String tipo, UUID cavernaId, UUID sessaoId,
                            LocalDate checkIn, LocalDate checkOut, Integer pessoas) {}
}
