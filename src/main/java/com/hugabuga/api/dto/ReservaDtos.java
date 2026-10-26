package com.hugabuga.api.dto;

import com.hugabuga.api.model.CobrancaExtra;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.model.TipoHospede;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReservaDtos {
    private ReservaDtos() {}

    /** Quem está pedindo: usuário logado (pode ser null) e/ou e-mail informado no pedido (pode ser null). */
    public record Solicitante(UUID usuarioId, String email) {}

    public record CriarReservaReq(@NotNull UUID cavernaId, @NotNull LocalDate checkIn, @NotNull LocalDate checkOut,
                                  @Min(1) int adultos, List<Integer> idadesCriancas, @Email String email,
                                  Integer precoEsperadoCentavos) {}

    public record HospedeDto(@NotBlank String nome, @NotNull TipoHospede tipo, Integer idade) {}

    public record HospedesReq(@NotBlank String responsavelNome, @NotBlank @Email String responsavelEmail,
                              @NotBlank String celular, @NotBlank String pais, @NotBlank String tipoDocumento,
                              @NotBlank String documento, String pedidoEspecial,
                              @NotEmpty @Valid List<HospedeDto> hospedes) {}

    public record ExtraItemReq(@NotBlank String extraId, Integer quantidade) {}

    public record ExtrasReq(@Valid List<ExtraItemReq> extras) {}

    public record CupomReq(@NotBlank String codigo) {}

    public record OssosReq(@Min(1) int quantidade) {}

    public record ExtraRes(String id, String nome, String descricao, String icone, int precoCentavos,
                           CobrancaExtra cobranca, boolean incluso) {}

    public record ExtraSelecionadoRes(String extraId, String nome, int quantidade, int precoUnitarioCentavos,
                                      int subtotalCentavos) {}

    public record ReservaRes(String codigo, StatusReserva status, UUID cavernaId, String cavernaNome,
                             String unidadeCodigo, LocalDate checkIn, LocalDate checkOut, int noites,
                             int adultos, int criancas, OffsetDateTime expiraEm, boolean prorrogada,
                             String responsavelNome, String responsavelEmail, String celular,
                             List<HospedeDto> hospedes, List<ExtraSelecionadoRes> extras, String cupom,
                             String pedidoEspecial, boolean lateCheckout, int valorDiarias, int valorExtras,
                             int desconto, int ossosUsados, int total, boolean pago,
                             OffsetDateTime checkinOnlineEm) {}

    public record AlterarDatasReq(@NotNull LocalDate checkIn, @NotNull LocalDate checkOut) {}

    /** diferencaCentavos > 0: virou novo pagamento; < 0: valor a devolver. */
    public record AlterarDatasRes(ReservaRes reserva, int diferencaCentavos) {}

    public record CancelarRes(ReservaRes reserva, int reembolsoCentavos, int multaCentavos) {}

    public record RelatoReq(@Min(1) @Max(5) int nota, @Size(max = 2000) String texto) {}
}
