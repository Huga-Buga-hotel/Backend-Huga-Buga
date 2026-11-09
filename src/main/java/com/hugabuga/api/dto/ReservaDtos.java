package com.hugabuga.api.dto;

import com.hugabuga.api.model.CobrancaExtra;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.model.TipoHospede;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ReservaDtos {
    private ReservaDtos() {}

    /**
     * Quem está pedindo: usuário logado (pode ser null) e/ou e-mail informado no header X-Responsavel-Email
     * (pode ser null). {@code admin} = token com role ADMIN, que pode agir em qualquer reserva.
     */
    public record Solicitante(UUID usuarioId, String email, boolean admin) {
        public Solicitante(UUID usuarioId, String email) { this(usuarioId, email, false); }
    }

    @Schema(description = "Passo 1 do checkout: cria a reserva PENDENTE e segura uma toca por 15 minutos.")
    public record CriarReservaReq(@NotNull UUID cavernaId,
                                  @NotNull @Schema(example = "2026-12-10") LocalDate checkIn,
                                  @NotNull @Schema(example = "2026-12-13") LocalDate checkOut,
                                  @Min(1) @Schema(example = "2") int adultos,
                                  @Schema(description = "Idade de cada criança (0 a 17). Acima de 12 conta como adulto.", example = "[9, 4]")
                                  List<Integer> idadesCriancas,
                                  @Email @Schema(description = "Obrigatório sem login: vira o e-mail do responsável.")
                                  String email,
                                  @Schema(description = "Valor das diárias que o front exibiu. Se mudou, 409 PRECO_ALTERADO.")
                                  Integer precoEsperadoCentavos) {}

    public record HospedeDto(@NotBlank String nome, @NotNull TipoHospede tipo,
                             @Schema(description = "Obrigatória para criança (0 a 17).") Integer idade) {}

    public record HospedesReq(@NotBlank String responsavelNome, @NotBlank @Email String responsavelEmail,
                              @NotBlank String celular, @NotBlank @Size(min = 2, max = 2) String pais,
                              @NotBlank @Schema(example = "CPF") String tipoDocumento,
                              @NotBlank String documento, @Size(max = 1000) String pedidoEspecial,
                              @NotEmpty @Valid List<HospedeDto> hospedes) {}

    public record ExtraItemReq(@NotBlank @Schema(example = "LATE_CHECKOUT") String extraId,
                               @Schema(description = "Só para extras POR_ESTADIA (1 a 10).") Integer quantidade) {}

    @Schema(description = "Lista COMPLETA de extras. Lista vazia = \"Pular extras\".")
    public record ExtrasReq(@Valid List<ExtraItemReq> extras) {}

    public record CupomReq(@NotBlank @Schema(example = "UGABUGA10") String codigo) {}

    public record ExtraRes(String id, String nome, String descricao, String icone, int precoCentavos,
                           CobrancaExtra cobranca, boolean incluso) {}

    public record ExtraSelecionadoRes(String extraId, String nome, int quantidade, int precoUnitarioCentavos,
                                      int subtotalCentavos) {}

    // ---------- combo quarto + Banquete ----------

    @Schema(description = "Combo quarto + Banquete: jantar todas as noites da estadia.")
    public record ComboRes(String id, String nome, String descricao, int precoAdultoCentavos,
                           int precoCriancaCentavos) {}

    @Schema(description = "Escolhe o combo e o horário do jantar (o mesmo em todas as noites).")
    public record ComboReq(@NotBlank @Schema(example = "JANTAR_DA_TRIBO") String comboId,
                           @NotNull @Schema(type = "string", example = "20:00") LocalTime horario) {}

    public record ComboSelecionadoRes(String comboId, String nome, LocalTime horario, int noites,
                                      int adultosPagantes, int criancasPagantes, int valorCentavos,
                                      @Schema(description = "Códigos BQ-XXXX das mesas reservadas, uma por noite.")
                                      List<String> mesas) {}

    @Schema(description = "Reserva completa (é também o voucher).")
    public record ReservaRes(String codigo, StatusReserva status, UUID cavernaId, String cavernaNome,
                             String unidadeCodigo, LocalDate checkIn, LocalDate checkOut, int noites,
                             int adultos, int criancas, OffsetDateTime expiraEm, boolean prorrogada,
                             String responsavelNome, String responsavelEmail, String celular,
                             List<HospedeDto> hospedes, List<ExtraSelecionadoRes> extras,
                             ComboSelecionadoRes combo, String cupom,
                             String pedidoEspecial, boolean lateCheckout, int valorDiarias, int valorExtras,
                             int valorCombo, int desconto, int total, boolean pago,
                             OffsetDateTime checkinOnlineEm) {}

    public record AlterarDatasReq(@NotNull LocalDate checkIn, @NotNull LocalDate checkOut) {}

    /** diferencaCentavos > 0: virou novo pagamento; < 0: valor a devolver. */
    public record AlterarDatasRes(ReservaRes reserva, int diferencaCentavos) {}

    public record CancelarRes(ReservaRes reserva, int reembolsoCentavos, int multaCentavos) {}
}
