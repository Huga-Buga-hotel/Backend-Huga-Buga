package com.hugabuga.api.dto;

import com.hugabuga.api.model.StatusMesa;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.model.TipoHospede;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContaDtos {
    private ContaDtos() {}

    public record PerfilRes(UUID id, String nome, String sobrenome, String email, String celular, String pais,
                            String tipoDocumento, String documentoMascarado, LocalDate dataNascimento,
                            boolean aceitaOfertas, int saldoOssos, String nivel, String idioma) {}

    /** PATCH: campos nulos não são alterados. */
    public record PerfilReq(String nome, String sobrenome, String celular, String pais, String tipoDocumento,
                            String numeroDocumento, LocalDate dataNascimento, Boolean aceitaOfertas) {}

    public record SenhaReq(@NotBlank String senhaAtual, @NotBlank @Size(min = 8) String novaSenha) {}

    public record MembroReq(@NotBlank String nome, @NotNull TipoHospede tipo, LocalDate dataNascimento,
                            String observacoes) {}

    public record MembroRes(UUID id, String nome, TipoHospede tipo, LocalDate dataNascimento, Integer idade,
                            String observacoes) {}

    public record MovimentoRes(int quantidade, String motivo, UUID reservaId, OffsetDateTime criadoEm) {}

    public record OssosRes(int saldo, long acumulado, String nivel, String proximoNivel, long faltam,
                           List<MovimentoRes> extrato) {}

    public record ReservaResumoRes(String codigo, UUID cavernaId, String cavernaNome, LocalDate checkIn,
                                   LocalDate checkOut, StatusReserva status, int total) {}

    public record MesaResumoRes(String codigo, LocalDate data, LocalTime horario, int pessoas, StatusMesa status) {}

    public record MinhasReservasRes(List<ReservaResumoRes> estadias, List<MesaResumoRes> mesas) {}
}
