package com.hugabuga.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ChatDtos {
    private ChatDtos() {}

    /** UUID gerado pelo app/site a cada conversa aberta. */
    public static final String UUID_REGEX = "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";

    @Schema(description = "Mensagem de texto para o Bugão. 'idSessao' é um UUID gerado pelo cliente por conversa.")
    public record ChatReq(@NotBlank @Size(max = 1000) String mensagem,
                          @NotBlank @Pattern(regexp = UUID_REGEX, message = "deve ser um UUID")
                          @Schema(example = "3f1c2b9e-8a4d-4f8e-9c1a-2b7d5e6f0a11") String idSessao) {}

    public record ChatRes(String resposta) {}

    @Schema(description = "Resposta a uma mensagem de voz: o texto reconhecido (para a bolha do usuário) e a resposta.")
    public record ChatAudioRes(String transcricao, String resposta) {}
}
