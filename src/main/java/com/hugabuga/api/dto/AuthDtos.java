package com.hugabuga.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record CadastroReq(@NotBlank String nome, @NotBlank String sobrenome,
                              @NotBlank @Email String email, @NotBlank @Size(min = 8) String senha,
                              @NotBlank String celular, @NotBlank @Size(min = 2, max = 2) String pais,
                              @NotBlank String tipoDocumento, @NotBlank String numeroDocumento,
                              LocalDate dataNascimento, boolean aceitaOfertas) {}

    public record LoginReq(@NotBlank @Email String email, @NotBlank String senha, boolean lembrar) {}

    public record RefreshReq(@NotBlank String refreshToken) {}

    public record TokensRes(String accessToken, String refreshToken, long expiraEmSegundos,
                            UUID usuarioId, String nome) {}
}
