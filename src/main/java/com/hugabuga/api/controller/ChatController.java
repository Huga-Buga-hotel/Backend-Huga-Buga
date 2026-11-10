package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.ChatDtos;
import com.hugabuga.api.dto.ChatDtos.ChatAudioRes;
import com.hugabuga.api.dto.ChatDtos.ChatReq;
import com.hugabuga.api.dto.ChatDtos.ChatRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Chatbot", description = "Bugão, o assistente virtual. Aberto para visitantes; com login a conversa fica ligada à conta.")
@RestController
@RequestMapping(SecurityConfig.API + "/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatbotService chatbot;

    @Operation(summary = "Mensagem de texto",
            description = "Use o mesmo idSessao (UUID gerado pelo cliente) durante a conversa para o Bugão lembrar do contexto.")
    @PostMapping
    public ChatRes texto(@Valid @RequestBody ChatReq req, Solicitante s) {
        return chatbot.enviar(req.mensagem(), req.idSessao(), s);
    }

    @Operation(summary = "Mensagem de voz",
            description = "multipart: 'audio' (m4a, mp3, ogg, wav, webm, flac, mp4; até 25 MB), 'idSessao' e 'idioma' opcional (pt, en, es).")
    @PostMapping(value = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatAudioRes audio(@RequestPart("audio") MultipartFile audio,
                              @RequestParam @Pattern(regexp = ChatDtos.UUID_REGEX) String idSessao,
                              @RequestParam(required = false) String idioma,
                              Solicitante s) {
        return chatbot.enviarAudio(audio, idSessao, idioma, s);
    }

    @Operation(summary = "Encerrar conversa", description = "Apaga o histórico da conversa (chame ao fechar o chat).")
    @DeleteMapping("/{idSessao}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrar(@Parameter(description = "UUID da conversa") @PathVariable
                         @Pattern(regexp = ChatDtos.UUID_REGEX) String idSessao, Solicitante s) {
        chatbot.encerrar(idSessao, s);
    }
}
