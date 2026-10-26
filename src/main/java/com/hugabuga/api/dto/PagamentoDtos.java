package com.hugabuga.api.dto;

import com.hugabuga.api.model.MetodoPagamento;
import com.hugabuga.api.model.StatusPagamento;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class PagamentoDtos {
    private PagamentoDtos() {}

    /** numeroCartao e cvv só existem na memória durante a requisição: nunca são gravados nem logados. */
    public record PagamentoReq(@NotNull MetodoPagamento metodo, Integer parcelas, String numeroCartao,
                               String validade, String cvv, boolean aceitoRegras) {}

    public record PagamentoRes(UUID id, String reservaCodigo, MetodoPagamento metodo, StatusPagamento status,
                               int valorCentavos, Integer parcelas, Integer valorParcelaCentavos,
                               String cartaoFinal, String cartaoBandeira, String pixCopiaCola,
                               OffsetDateTime pixExpiraEm, String motivoRecusa) {}
}
