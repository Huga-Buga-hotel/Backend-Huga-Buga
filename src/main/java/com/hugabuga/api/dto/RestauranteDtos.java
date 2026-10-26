package com.hugabuga.api.dto;

import com.hugabuga.api.model.CategoriaCardapio;
import com.hugabuga.api.model.StatusMesa;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class RestauranteDtos {
    private RestauranteDtos() {}

    public record TurnoRes(String nome, LocalTime abre, LocalTime fecha) {}

    public record RestauranteRes(List<TurnoRes> turnos, boolean abertoAgora, String turnoAtual,
                                 int descontoHospedePct) {}

    public record ItemCardapioRes(UUID id, String nome, String descricao, int precoCentavos,
                                  CategoriaCardapio categoria, List<String> tags) {}

    /** status: "LIVRE" ou "LOTADO". */
    public record HorarioRes(LocalTime horario, String status, int vagasRestantes) {}

    public record MesaReq(@NotBlank String codigoReserva, String nomeContato, String celularContato,
                          @NotNull LocalDate data, @NotNull LocalTime horario,
                          @Min(1) @Max(12) int pessoas, @Min(0) int cadeirinhas, boolean pertoFogueira) {}

    public record MesaRes(String codigo, LocalDate data, LocalTime horario, int pessoas, int cadeirinhas,
                          boolean pertoFogueira, StatusMesa status, String nomeContato, int descontoHospedePct) {}
}
