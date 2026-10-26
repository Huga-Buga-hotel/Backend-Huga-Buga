package com.hugabuga.api;

import com.hugabuga.api.dto.PagamentoDtos.PagamentoReq;
import com.hugabuga.api.dto.ReservaDtos.CriarReservaReq;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.MetodoPagamento;
import com.hugabuga.api.model.Reserva;
import com.hugabuga.api.repository.*;
import com.hugabuga.api.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Base dos testes: sobe UM PostgreSQL real (Testcontainers, exige Docker) compartilhado por todas as classes,
 * aplica as migrações V1/V2 (Flyway) e roda cada teste dentro de uma transação que sofre rollback.
 *
 * Por que PostgreSQL e não H2: a proteção contra reserva dupla é uma restrição de exclusão (btree_gist),
 * que não existe no H2.
 *
 * Usa os dados do V2: Toca do Mamute (T-07, T-08, T-09, R$ 520/noite, capacidade 5), cupom UGABUGA10 e a
 * Marina (450 ossos). Os testes usam datas relativas a hoje para não "envelhecerem".
 *
 * Se estiver no Testcontainers 1.x, troque o import por org.testcontainers.containers.PostgreSQLContainer.
 */
@SpringBootTest
@ActiveProfiles("dev") // habilita a simulação do Pix e traz o segredo JWT de desenvolvimento
@Transactional
public abstract class IntegrationTestBase {

    // Singleton: iniciado uma única vez e encerrado pelo Ryuk ao fim da JVM.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static final String EMAIL_MARINA = "marina.souza@example.com";
    protected static final String TOCA = "toca-do-mamute";
    protected static final String CARTAO_APROVADO = "4111111111111111"; // Visa de teste, válido no Luhn
    protected static final String CARTAO_SALDO_INSUFICIENTE = "4000000000000002"; // final 0002
    protected static final String CARTAO_EXPIRADO = "4000000000000069";           // final 0069

    @Autowired protected ReservaService reservaService;
    @Autowired protected CavernaService cavernaService;
    @Autowired protected PagamentoService pagamentoService;
    @Autowired protected AuthService authService;
    @Autowired protected JwtService jwtService;

    @Autowired protected ReservaRepository reservaRepository;
    @Autowired protected CavernaRepository cavernaRepository;
    @Autowired protected UnidadeCavernaRepository unidadeRepository;
    @Autowired protected TarifaEspecialRepository tarifaRepository;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected PagamentoRepository pagamentoRepository;
    @Autowired protected CupomRepository cupomRepository;
    @Autowired protected MovimentoOssosRepository movimentoRepository;

    /** Os feriados do seed poderiam cair nas datas de teste e mudar os valores: cada teste parte sem tarifas. */
    @BeforeEach
    void semTarifasEspeciais() {
        tarifaRepository.deleteAll();
        tarifaRepository.flush();
    }

    // ---------- atalhos ----------

    protected LocalDate dias(int n) { return Util.hoje().plusDays(n); }

    protected UUID marinaId() { return usuarioRepository.findByEmailIgnoreCase(EMAIL_MARINA).orElseThrow().getId(); }

    protected Solicitante marina() { return new Solicitante(marinaId(), null); }

    protected Solicitante anonimo(String email) { return new Solicitante(null, email); }

    protected UUID cavernaId(String slug) { return cavernaRepository.findBySlugAndAtivaTrue(slug).orElseThrow().getId(); }

    protected ReservaRes criar(String slug, LocalDate in, LocalDate out, int adultos, List<Integer> idades, Solicitante s) {
        String email = s.usuarioId() == null ? s.email() : null;
        return reservaService.criar(new CriarReservaReq(cavernaId(slug), in, out, adultos, idades, email, null), s);
    }

    /** Toca do Mamute, 2 adultos, 3 noites (R$ 1.560,00), 30 dias à frente. */
    protected ReservaRes criarPadrao(Solicitante s) { return criar(TOCA, dias(30), dias(33), 2, List.of(), s); }

    protected Reserva reserva(String codigo) { return reservaRepository.findByCodigo(codigo).orElseThrow(); }

    protected PagamentoReq cartao(String numero, int parcelas) {
        return new PagamentoReq(MetodoPagamento.CARTAO, parcelas, numero, "12/30", "123", true);
    }

    protected PagamentoReq pix() { return new PagamentoReq(MetodoPagamento.PIX, null, null, null, null, false); }

    protected PagamentoReq naChegada() { return new PagamentoReq(MetodoPagamento.NA_CHEGADA, null, null, null, null, false); }

    protected ApiException erro(Executable acao) { return assertThrows(ApiException.class, acao); }

    protected void assertErro(ApiException e, HttpStatus status, String codigo) {
        assertEquals(status, e.getStatus());
        assertEquals(codigo, e.getCodigo());
    }
}
