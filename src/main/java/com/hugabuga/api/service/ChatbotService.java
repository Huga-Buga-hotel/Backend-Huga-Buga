package com.hugabuga.api.service;

import com.hugabuga.api.dto.ChatDtos.ChatAudioRes;
import com.hugabuga.api.dto.ChatDtos.ChatRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.erro.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ponte com a API do chatbot (Python, repositório chatbot-huga-buga-hotel), que roda numa rede privada.
 *
 * O app e o site falam só com este backend: a chave do Groq e o chatbot nunca ficam expostos.
 * O id da conversa enviado ao chatbot é montado AQUI, separado por dono (usuário logado ou anônimo),
 * para ninguém conseguir escrever no histórico da conversa de outra pessoa.
 */
@Slf4j
@Service
public class ChatbotService {

    public static final String HEADER_TOKEN = "X-Chatbot-Token";

    private static final Set<String> FORMATOS_AUDIO =
            Set.of("flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");
    private static final Pattern IDIOMA = Pattern.compile("[a-z]{2}");

    private final RestClient chatbot;

    public ChatbotService(@Value("${hugabuga.chatbot.url}") String url,
                          @Value("${hugabuga.chatbot.token:}") String token,
                          @Value("${hugabuga.chatbot.timeout-segundos:60}") int timeoutSegundos) {
        // HTTP/1.1: em http:// o padrão (HTTP/2) manda "Upgrade: h2c" com o corpo, e o uvicorn descarta o corpo
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build();
        var fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos)); // o agente pode chamar várias tools
        this.chatbot = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeaders(h -> { if (!token.isBlank()) h.set(HEADER_TOKEN, token); })
                .build();
    }

    public ChatRes enviar(String mensagem, String idSessao, Solicitante s) {
        Map<?, ?> r = chamar(false, () -> chatbot.post().uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mensagem", mensagem.trim(), "id_sessao", sessao(idSessao, s)))
                .retrieve().body(Map.class));
        return new ChatRes(texto(r, "resposta"));
    }

    public ChatAudioRes enviarAudio(MultipartFile audio, String idSessao, String idioma, Solicitante s) {
        String extensao = extensao(audio.getOriginalFilename());
        if (!FORMATOS_AUDIO.contains(extensao)) {
            throw Util.validacao("Formato de áudio não suportado. Envie m4a, mp3, ogg, wav, webm, flac ou mp4.");
        }
        if (audio.isEmpty()) throw Util.validacao("O áudio veio vazio.");
        if (idioma != null && !IDIOMA.matcher(idioma).matches()) idioma = null; // dica opcional, nunca bloqueia

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw Util.validacao("Não conseguimos ler o áudio.");
        }
        // Nome gerado aqui: o do cliente não é repassado
        var arquivo = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "mensagem." + extensao; }
        };
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("audio", arquivo);
        form.add("id_sessao", sessao(idSessao, s));
        if (idioma != null) form.add("idioma", idioma);

        Map<?, ?> r = chamar(true, () -> chatbot.post().uri("/chat/audio")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve().body(Map.class));
        return new ChatAudioRes(texto(r, "transcricao"), texto(r, "resposta"));
    }

    /** Apaga o histórico. Falha aqui não incomoda o usuário (a conversa some quando o chatbot reinicia). */
    public void encerrar(String idSessao, Solicitante s) {
        try {
            chatbot.delete().uri("/chat/{id}", sessao(idSessao, s)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Chatbot não apagou a conversa: {}", e.getMessage());
        }
    }

    /** "usuario-<uuid>-<conversa>" ou "anonimo-<conversa>". */
    static String sessao(String idSessao, Solicitante s) {
        String conversa = idSessao.toLowerCase(Locale.ROOT);
        return s.usuarioId() != null ? "usuario-" + s.usuarioId() + "-" + conversa : "anonimo-" + conversa;
    }

    private Map<?, ?> chamar(boolean audio, java.util.function.Supplier<Map<?, ?>> chamada) {
        try {
            Map<?, ?> r = chamada.get();
            if (r == null) throw indisponivel();
            return r;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (audio && status == 413) {
                throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "ARQUIVO_GRANDE", "Ugh! Áudio grande demais.",
                        "O áudio passa de 25 MB e não foi enviado. Grave uma mensagem mais curta.");
            }
            if (audio && status == 422) { // 422 do /chat/audio = transcrição vazia
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_SEM_FALA", "Ugh! Não entendi o áudio.",
                        "Não reconhecemos nenhuma fala no áudio. Grave de novo, perto do microfone, ou escreva a pergunta.");
            }
            if (audio && (status == 400 || status == 415)) throw Util.validacao("O chatbot recusou o áudio.");
            // demais casos (inclusive 401 de token errado e 422 de contrato) são falha de integração, não do usuário
            log.error("Chatbot respondeu {}: {}", status, e.getResponseBodyAsString());
            throw indisponivel();
        } catch (RestClientException e) {
            log.error("Chatbot fora do ar: {}", e.getMessage());
            throw indisponivel();
        }
    }

    private static String texto(Map<?, ?> r, String campo) {
        Object v = r.get(campo);
        if (v == null) throw indisponivel();
        return v.toString();
    }

    private static String extensao(String nome) {
        if (nome == null) return "";
        int ponto = nome.lastIndexOf('.');
        return ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
    }

    private static ApiException indisponivel() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "CHATBOT_INDISPONIVEL", "Ugh! O Bugão está cochilando.",
                "O assistente não respondeu agora e sua mensagem não foi processada. Tente de novo em instantes "
                        + "ou fale com a recepção: (11) 99999-9999.");
    }
}
