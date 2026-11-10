package com.hugabuga.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ponte backend → chatbot, contra um chatbot falso (servidor HTTP local, sem Groq). */
class ChatbotTest extends IntegrationTestBase {

    static final HttpServer CHATBOT_FALSO;
    static final AtomicReference<String> ULTIMO_CORPO = new AtomicReference<>();
    static final AtomicReference<String> ULTIMO_TOKEN = new AtomicReference<>();
    static final AtomicReference<String> ULTIMA_ROTA = new AtomicReference<>();
    static final AtomicReference<String> ULTIMO_UPGRADE = new AtomicReference<>();
    static final AtomicInteger STATUS = new AtomicInteger(200);

    static {
        try {
            CHATBOT_FALSO = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        CHATBOT_FALSO.createContext("/", troca -> {
            ULTIMA_ROTA.set(troca.getRequestMethod() + " " + troca.getRequestURI().getPath());
            ULTIMO_TOKEN.set(troca.getRequestHeaders().getFirst("X-Chatbot-Token"));
            ULTIMO_UPGRADE.set(troca.getRequestHeaders().getFirst("Upgrade"));
            ULTIMO_CORPO.set(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String resposta = troca.getRequestURI().getPath().equals("/chat/audio")
                    ? "{\"transcricao\":\"Aceitam pets?\",\"resposta\":\"Sim!\"}"
                    : "{\"resposta\":\"Check-in às 13h!\"}";
            byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(STATUS.get(), bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        CHATBOT_FALSO.start();
    }

    @DynamicPropertySource
    static void chatbot(DynamicPropertyRegistry registry) {
        registry.add("hugabuga.chatbot.url", () -> "http://127.0.0.1:" + CHATBOT_FALSO.getAddress().getPort());
        registry.add("hugabuga.chatbot.token", () -> "segredo-de-teste");
    }

    @AfterAll
    static void parar() {
        CHATBOT_FALSO.stop(0);
    }

    @Autowired WebApplicationContext contexto;
    MockMvc mvc;
    final String conversa = UUID.randomUUID().toString();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        STATUS.set(200);
    }

    @Test
    void visitanteConversaSemLoginESessaoFicaNoEspacoAnonimo() throws Exception {
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Que horas é o check-in?\",\"idSessao\":\"" + conversa + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta").value("Check-in às 13h!"));

        assertEquals("POST /chat", ULTIMA_ROTA.get());
        assertEquals("segredo-de-teste", ULTIMO_TOKEN.get());
        // o uvicorn descarta o corpo de requisições com "Upgrade: h2c" (padrão do HttpClient do Java)
        assertEquals(null, ULTIMO_UPGRADE.get());
        assertTrue(ULTIMO_CORPO.get().contains("\"id_sessao\":\"anonimo-" + conversa + "\""), ULTIMO_CORPO.get());
    }

    @Test
    void comLoginASessaoFicaPresaAoUsuario() throws Exception {
        String token = jwtService.gerarAcesso(usuarioRepository.findById(marinaId()).orElseThrow());
        mvc.perform(post("/api/v1/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"oi\",\"idSessao\":\"" + conversa + "\"}"))
                .andExpect(status().isOk());

        assertTrue(ULTIMO_CORPO.get().contains("\"id_sessao\":\"usuario-" + marinaId() + "-" + conversa + "\""));
    }

    @Test
    void idDeSessaoPrecisaSerUuid() throws Exception {
        // impede que alguém mande "usuario-<id de outra pessoa>" e escreva na conversa dela
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"oi\",\"idSessao\":\"usuario-123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audioERepassadoComNomeGeradoPeloBackend() throws Exception {
        var audio = new MockMultipartFile("audio", "../../meu audio.M4A", "audio/mp4", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/v1/chat/audio").file(audio).param("idSessao", conversa).param("idioma", "pt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcricao").value("Aceitam pets?"))
                .andExpect(jsonPath("$.resposta").value("Sim!"));

        assertEquals("POST /chat/audio", ULTIMA_ROTA.get());
        assertTrue(ULTIMO_CORPO.get().contains("filename=\"mensagem.m4a\""));
    }

    @Test
    void audioEmFormatoErradoNemChegaNoChatbot() throws Exception {
        ULTIMA_ROTA.set(null);
        var audio = new MockMultipartFile("audio", "virus.exe", "application/octet-stream", new byte[]{1});
        mvc.perform(multipart("/api/v1/chat/audio").file(audio).param("idSessao", conversa))
                .andExpect(status().isBadRequest());
        assertEquals(null, ULTIMA_ROTA.get());
    }

    @Test
    void chatbotForaDoArViraErroNoFormatoPadrao() throws Exception {
        STATUS.set(502);
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"oi\",\"idSessao\":\"" + conversa + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigo").value("CHATBOT_INDISPONIVEL"))
                .andExpect(jsonPath("$.mensagem").value(containsString("recepção")));
    }

    @Test
    void encerrarApagaAConversaNoChatbot() throws Exception {
        mvc.perform(delete("/api/v1/chat/" + conversa)).andExpect(status().isNoContent());
        assertEquals("DELETE /chat/anonimo-" + conversa, ULTIMA_ROTA.get());
    }
}
