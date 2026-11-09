package com.hugabuga.api.dto;

import com.hugabuga.api.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    @Schema(description = "Cadastro de visitante. Toda conta nova nasce com role VISITANTE.")
    public record CadastroReq(@NotBlank String nome, @NotBlank String sobrenome,
                              @NotBlank @Email String email, @NotBlank @Size(min = 8) String senha,
                              @NotBlank String celular, @NotBlank @Size(min = 2, max = 2) @Schema(example = "BR") String pais,
                              @NotBlank @Schema(example = "CPF") String tipoDocumento, @NotBlank String numeroDocumento,
                              LocalDate dataNascimento, boolean aceitaOfertas) {}

    public record LoginReq(@NotBlank @Email @Schema(example = "marina.souza@example.com") String email,
                           @NotBlank @Schema(example = "Uga!Buga2026") String senha,
                           @Schema(description = "\"Lembrar de mim\": refresh de 30 dias (sem: 1 dia).") boolean lembrar) {}

    public record RefreshReq(@NotBlank String refreshToken) {}

    public record TokensRes(String accessToken, String refreshToken, long expiraEmSegundos,
                            UUID usuarioId, String nome, Role role) {}

    public record EsqueciSenhaReq(@NotBlank @Email String email) {}

    @Schema(description = "A resposta é sempre a mesma, exista ou não a conta. 'tokenDev' só vem no perfil dev "
            + "(simula o link que iria por e-mail).")
    public record EsqueciSenhaRes(String mensagem, String tokenDev) {}

    public record RedefinirSenhaReq(@NotBlank String token, @NotBlank @Size(min = 8) String novaSenha) {}
}
