package com.hugabuga.api;

import com.hugabuga.api.dto.AuthDtos.CadastroReq;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste 7: usuário A tentando acessar a reserva do usuário B deve dar 403.
 * Cobre a regra no service e a API de ponta a ponta (token JWT, header X-Responsavel-Email, 401 e 403).
 */
class AutorizacaoTest extends IntegrationTestBase {

    @Autowired WebApplicationContext contexto;
    MockMvc mvc;

    private String codigoDaMarina;
    private String tokenMarina;
    private String tokenOutroUsuario;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();

        codigoDaMarina = criarPadrao(marina()).codigo();
        tokenMarina = jwtService.gerarAcesso(usuarioRepository.findById(marinaId()).orElseThrow());
        tokenOutroUsuario = authService.cadastrar(new CadastroReq("Outro", "Hospede", "outro.hospede@example.com",
                "Senha@12345", "+5511999990000", "BR", "CPF", "52998224725", LocalDate.of(1990, 1, 1), false)).accessToken();
    }

    private String bearer(String token) { return "Bearer " + token; }

    // ---------- na camada de service ----------

    @Test
    void usuarioANaoVeReservaDoUsuarioB() {
        UUID outroId = usuarioRepository.findByEmailIgnoreCase("outro.hospede@example.com").orElseThrow().getId();
        var e = erro(() -> reservaService.voucher(codigoDaMarina, new Solicitante(outroId, null)));
        assertErro(e, HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }

    @Test
    void semContaEPrecisoInformarOEmailDoResponsavel() {
        ReservaRes r = criarPadrao(anonimo("cliente@example.com"));
        assertDoesNotThrow(() -> reservaService.voucher(r.codigo(), anonimo("cliente@example.com")));
        assertDoesNotThrow(() -> reservaService.voucher(r.codigo(), anonimo(" CLIENTE@example.com ")), "sem diferenciar maiúsculas");

        assertErro(erro(() -> reservaService.voucher(r.codigo(), anonimo("outro@example.com"))), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
        assertErro(erro(() -> reservaService.voucher(r.codigo(), anonimo(null))), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }

    @Test
    void naoDaParaMexerNaReservaDeOutroEmNenhumaAcao() {
        Solicitante intruso = anonimo("intruso@example.com");
        assertErro(erro(() -> reservaService.prorrogar(codigoDaMarina, intruso)), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
        assertErro(erro(() -> reservaService.cancelar(codigoDaMarina, intruso)), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
        assertErro(erro(() -> pagamentoService.pagar(codigoDaMarina, pix(), intruso)), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
        assertErro(erro(() -> reservaService.checkinOnline(codigoDaMarina, intruso)), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }

    @Test
    void naoDaParaConsultarPagamentoDeOutro() {
        var p = pagamentoService.pagar(codigoDaMarina, pix(), marina());
        UUID outroId = usuarioRepository.findByEmailIgnoreCase("outro.hospede@example.com").orElseThrow().getId();
        var e = erro(() -> pagamentoService.consultar(p.id(), new Solicitante(outroId, null)));
        assertErro(e, HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }

    @Test
    void buscaSemContaExigeCodigoEEmailCorretos() {
        assertDoesNotThrow(() -> reservaService.buscarSemConta(codigoDaMarina, "MARINA.souza@example.com"));
        // código certo com e-mail errado e código inexistente respondem igual: não revelam se o código existe
        var errado = erro(() -> reservaService.buscarSemConta(codigoDaMarina, "outro@example.com"));
        var inexistente = erro(() -> reservaService.buscarSemConta("HB-ZZZZ", EMAIL_MARINA));
        assertErro(errado, HttpStatus.NOT_FOUND, "NAO_ENCONTRADO");
        assertErro(inexistente, HttpStatus.NOT_FOUND, "NAO_ENCONTRADO");
    }

    // ---------- pela API (HTTP) ----------

    @Test
    void usuarioBLevaDeFatoUm403NaApi() throws Exception {
        mvc.perform(get("/api/v1/reservas/" + codigoDaMarina).header("Authorization", bearer(tokenOutroUsuario)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("\"codigo\":\"SEM_PERMISSAO\"")));
    }

    @Test
    void donaDaReservaVeComOTokenDela() throws Exception {
        mvc.perform(get("/api/v1/reservas/" + codigoDaMarina).header("Authorization", bearer(tokenMarina)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(codigoDaMarina)));
    }

    @Test
    void semTokenNemHeaderDa403EComHeaderCertoDa200() throws Exception {
        mvc.perform(get("/api/v1/reservas/" + codigoDaMarina))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservas/" + codigoDaMarina).header("X-Responsavel-Email", EMAIL_MARINA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reservas/" + codigoDaMarina).header("X-Responsavel-Email", "outro@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rotasQueExigemLoginDao401SemToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("\"codigo\":\"NAO_AUTENTICADO\"")));

        mvc.perform(post("/api/v1/reservas/" + codigoDaMarina + "/ossos")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalidoDa401() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("NAO_AUTENTICADO")));
    }

    @Test
    void tokenInvalidoNaoAtrapalhaOLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer lixo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL_MARINA + "\",\"senha\":\"Uga!Buga2026\",\"lembrar\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void perfilNaoVazaSenhaNemDocumentoCompleto() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", bearer(tokenMarina)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(EMAIL_MARINA)))
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("12345678909"))));   // só os 4 últimos saem
    }

    @Test
    void rotaInexistenteSemTokenNaoFicaAberta() throws Exception {
        mvc.perform(get("/api/v1/rota-que-nao-existe")).andExpect(status().isUnauthorized());
    }
}
