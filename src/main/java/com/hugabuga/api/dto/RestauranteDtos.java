package com.hugabuga.api.dto;

import com.hugabuga.api.model.CategoriaCardapio;
import com.hugabuga.api.model.OrigemMesa;
import com.hugabuga.api.model.StatusMesa;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class RestauranteDtos {
    private RestauranteDtos() {}

    public record TurnoRes(String nome, LocalTime abre, LocalTime fecha) {}

    public record RestauranteRes(List<TurnoRes> turnos, boolean abertoAgora, String turnoAtual,
                                 int descontoHospedePct, int capacidadePessoas) {}

    public record ItemCardapioRes(UUID id, String nome, String descricao, int precoCentavos,
                                  CategoriaCardapio categoria, List<String> tags) {}

    /** status: "LIVRE" ou "LOTADO". */
    public record HorarioRes(LocalTime horario, String status, int vagasRestantes) {}

    @Schema(description = """
            Reserva de mesa. Com 'codigoReserva' é de hóspede (estadia CONFIRMADA, ganha desconto).
            Sem ele é de visitante de fora: nomeContato, celularContato e e-mail (campo ou conta logada) são obrigatórios.""")
    public record MesaReq(@Schema(example = "HB-4F7K") String codigoReserva,
                          String nomeContato, @Email String emailContato, String celularContato,
                          @NotNull LocalDate data,
                          @NotNull @Schema(type = "string", example = "20:00") LocalTime horario,
                          @Min(1) @Max(12) int pessoas,
                          @Min(0) @Schema(description = "Cadeirinhas infantis.") int cadeirinhas,
                          @Schema(description = "Mesa perto da fogueira.") boolean pertoFogueira) {}

    public record MesaRes(String codigo, OrigemMesa origem, String codigoReserva, LocalDate data, LocalTime horario,
                          int pessoas, int cadeirinhas, boolean pertoFogueira, StatusMesa status, String nomeContato,
                          @Schema(description = "15 para hóspede e combo; 0 para visitante de fora.") int descontoHospedePct) {}
}
