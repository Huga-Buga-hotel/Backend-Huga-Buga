package com.hugabuga.api.dto;

import com.hugabuga.api.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContaDtos {
    private ContaDtos() {}

    public record PerfilRes(UUID id, String nome, String sobrenome, String email, String celular, String pais,
                            String tipoDocumento,
                            @Schema(description = "Só os 4 últimos caracteres (LGPD).") String documentoMascarado,
                            LocalDate dataNascimento, boolean aceitaOfertas, Role role, String idioma) {}

    /** PATCH: campos nulos não são alterados. */
    public record PerfilReq(String nome, String sobrenome, String celular, String pais, String tipoDocumento,
                            String numeroDocumento, LocalDate dataNascimento, Boolean aceitaOfertas) {}

    public record SenhaReq(@NotBlank String senhaAtual, @NotBlank @Size(min = 8) String novaSenha) {}

    @Schema(description = "Idioma e notificações (itens \"Notificações\" e \"Idioma\" do perfil).")
    public record PreferenciasRes(String idioma, boolean notificacoesEmail, boolean notificacoesWhatsapp,
                                  boolean aceitaOfertas) {}

    /** PUT: campos nulos não são alterados. */
    public record PreferenciasReq(@Pattern(regexp = "pt-BR|en|es") @Schema(example = "pt-BR") String idioma,
                                  Boolean notificacoesEmail, Boolean notificacoesWhatsapp, Boolean aceitaOfertas) {}

    public record MembroReq(@NotBlank String nome, @NotNull TipoHospede tipo, LocalDate dataNascimento,
                            @Size(max = 500) String observacoes) {}

    public record MembroRes(UUID id, String nome, TipoHospede tipo, LocalDate dataNascimento, Integer idade,
                            String observacoes) {}

    public record ReservaResumoRes(String codigo, UUID cavernaId, String cavernaNome, String cavernaSlug,
                                   String fotoUrl, LocalDate checkIn, LocalDate checkOut, int noites,
                                   int adultos, int criancas, StatusReserva status, int total, boolean pago,
                                   @Schema(description = "\"20 dias pra caverna\". Nulo se já passou.") Integer diasParaCheckIn,
                                   boolean temCombo, boolean podeAvaliar) {}

    public record MesaResumoRes(String codigo, LocalDate data, LocalTime horario, int pessoas, StatusMesa status,
                                OrigemMesa origem) {}

    @Schema(description = "Abas de Minhas reservas. 'contagem' traz os números de cada aba (Próximas 2 · Passadas 0 · ...).")
    public record MinhasReservasRes(List<ReservaResumoRes> estadias, List<MesaResumoRes> mesas,
                                    Map<String, Integer> contagem) {}

    @Schema(description = "Histórico de pagamentos (item \"Pagamentos\" do perfil). Cartão: só final e bandeira.")
    public record PagamentoResumoRes(UUID id, String reservaCodigo, MetodoPagamento metodo, StatusPagamento status,
                                     int valorCentavos, Integer parcelas, String cartaoFinal, String cartaoBandeira,
                                     OffsetDateTime criadoEm, OffsetDateTime aprovadoEm) {}
}
