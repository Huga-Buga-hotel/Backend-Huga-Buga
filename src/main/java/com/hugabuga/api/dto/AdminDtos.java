package com.hugabuga.api.dto;

import com.hugabuga.api.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Painel da equipe do hotel (role ADMIN). */
public final class AdminDtos {
    private AdminDtos() {}

    @Schema(description = "Resumo do dia: ocupação das 25 tocas, entradas e saídas, receita do mês e lotação do Banquete.")
    public record DashboardRes(LocalDate data, int tocasTotal, int tocasOcupadas, int ocupacaoPct,
                               List<ReservaAdminRes> checkInsDoDia, List<ReservaAdminRes> checkOutsDoDia,
                               long receitaDoMesCentavos, int pessoasNoBanquete, int capacidadeBanquete) {}

    public record ReservaAdminRes(String codigo, StatusReserva status, String cavernaNome, String unidadeCodigo,
                                  LocalDate checkIn, LocalDate checkOut, String responsavelNome,
                                  String responsavelEmail, int adultos, int criancas, int total, boolean temCombo,
                                  OffsetDateTime criadoEm) {}

    @Schema(description = "Situação de cada toca em uma noite: LIVRE, OCUPADA ou MANUTENCAO (unidade desativada).")
    public record OcupacaoUnidadeRes(UUID unidadeId, String codigo, String cavernaNome, String situacao,
                                     String reservaCodigo, StatusReserva reservaStatus, LocalDate checkIn,
                                     LocalDate checkOut, String responsavelNome) {}

    @Schema(description = "Ativa ou tira uma toca de circulação (manutenção). Reservas existentes não são afetadas.")
    public record UnidadeReq(@NotNull Boolean ativa) {}

    public record CavernaAdminRes(UUID id, String slug, String nome, CategoriaCaverna categoria, int capacidade,
                                  int precoBaseCentavos, boolean ativa, long unidades, long unidadesAtivas,
                                  double notaMedia, int totalRelatos) {}

    /** PATCH: campos nulos não são alterados. */
    public record CavernaAdminReq(@Size(min = 2, max = 255) String nome, @Size(max = 255) String resumo,
                                  String descricao, @Min(0) Integer precoBaseCentavos, Boolean ativa) {}

    public record OcupacaoHorarioRes(LocalTime horario, int pessoas, int lugaresLivres) {}

    public record MesaAdminRes(String codigo, OrigemMesa origem, StatusMesa status, LocalTime horario, int pessoas,
                               int cadeirinhas, boolean pertoFogueira, String nomeContato, String celularContato,
                               String emailContato, String reservaCodigo) {}

    @Schema(description = "Mesas do dia e quantas pessoas estão no salão em cada horário (capacidade de 50).")
    public record MesasDoDiaRes(LocalDate data, int capacidadePessoas, List<OcupacaoHorarioRes> ocupacao,
                                List<MesaAdminRes> mesas) {}

    public record UsuarioAdminRes(UUID id, String nome, String sobrenome, String email, Role role,
                                  OffsetDateTime criadoEm) {}

    public record RoleReq(@NotNull Role role) {}
}
