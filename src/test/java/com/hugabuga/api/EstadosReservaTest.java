package com.hugabuga.api;

import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.Reserva;
import com.hugabuga.api.model.StatusReserva;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.hugabuga.api.model.StatusReserva.*;
import static org.junit.jupiter.api.Assertions.*;

/** Teste 4: transições válidas e inválidas da máquina de estados (seção 7 do guia). */
class EstadosReservaTest extends IntegrationTestBase {

    /** A especificação da seção 7, escrita de forma independente do código de produção. */
    private static final Map<StatusReserva, Set<StatusReserva>> ESPERADO = new EnumMap<>(StatusReserva.class);
    static {
        ESPERADO.put(PENDENTE, EnumSet.of(AGUARDANDO_PAGAMENTO, CONFIRMADA, EXPIRADA, CANCELADA));
        ESPERADO.put(AGUARDANDO_PAGAMENTO, EnumSet.of(CONFIRMADA, EXPIRADA));
        ESPERADO.put(CONFIRMADA, EnumSet.of(CANCELADA, CONCLUIDA));
        ESPERADO.put(CANCELADA, EnumSet.noneOf(StatusReserva.class));
        ESPERADO.put(EXPIRADA, EnumSet.noneOf(StatusReserva.class));
        ESPERADO.put(CONCLUIDA, EnumSet.noneOf(StatusReserva.class));
    }

    static Stream<Arguments> todasAsTransicoes() {
        return Stream.of(StatusReserva.values()).flatMap(de ->
                Stream.of(StatusReserva.values()).map(para -> Arguments.of(de, para, ESPERADO.get(de).contains(para))));
    }

    @ParameterizedTest(name = "{0} -> {1}: permitida = {2}")
    @MethodSource("todasAsTransicoes")
    void transicoes(StatusReserva de, StatusReserva para, boolean permitida) {
        Reserva r = reserva(criarPadrao(anonimo("cliente@example.com")).codigo());
        r.setStatus(de);

        if (permitida) {
            assertDoesNotThrow(() -> reservaService.mudarStatus(r, para));
            assertEquals(para, r.getStatus());
        } else {
            var e = erro(() -> reservaService.mudarStatus(r, para));
            assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
            assertEquals(de, r.getStatus(), "uma transição recusada não pode alterar o estado");
        }
    }

    @Test
    void reservaNasceComSegurancaDe15MinutosEPendente() {
        ReservaRes r = criarPadrao(anonimo("cliente@example.com"));
        assertEquals(PENDENTE, r.status());
        assertFalse(r.prorrogada());
        long minutos = java.time.Duration.between(com.hugabuga.api.service.Util.agora(), r.expiraEm()).toMinutes();
        assertTrue(minutos >= 14 && minutos <= 15, "segura a toca por 15 min, mas foi " + minutos);
    }

    @Test
    void prorrogarAdiciona10MinutosUmaUnicaVez() {
        Solicitante s = anonimo("cliente@example.com");
        ReservaRes r = criarPadrao(s);
        ReservaRes depois = reservaService.prorrogar(r.codigo(), s);

        assertTrue(depois.prorrogada());
        assertEquals(10, java.time.Duration.between(r.expiraEm(), depois.expiraEm()).toMinutes());

        var e = erro(() -> reservaService.prorrogar(r.codigo(), s));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }

    @Test
    void acaoEmReservaExpiradaDa410() {
        Solicitante s = anonimo("cliente@example.com");
        ReservaRes r = criarPadrao(s);
        Reserva e = reserva(r.codigo());
        e.setStatus(EXPIRADA);
        reservaRepository.saveAndFlush(e);

        assertErro(erro(() -> reservaService.prorrogar(r.codigo(), s)), HttpStatus.GONE, "RESERVA_EXPIRADA");
        assertErro(erro(() -> pagamentoService.pagar(r.codigo(), pix(), s)), HttpStatus.GONE, "RESERVA_EXPIRADA");
    }

    @Test
    void pendenteComCronometroVencidoDa410MesmoAntesDoJob() {
        Solicitante s = anonimo("cliente@example.com");
        ReservaRes r = criarPadrao(s);
        Reserva e = reserva(r.codigo());
        e.setExpiraEm(com.hugabuga.api.service.Util.agora().minusMinutes(1));
        reservaRepository.saveAndFlush(e);

        assertErro(erro(() -> reservaService.prorrogar(r.codigo(), s)), HttpStatus.GONE, "RESERVA_EXPIRADA");
    }

    @Test
    void jobDeExpiracaoMarcaVencidasELiberaAUnidade() {
        Solicitante s = anonimo("cliente@example.com");
        for (int i = 0; i < 2; i++) criar(TOCA, dias(40), dias(43), 2, List.of(), anonimo("x" + i + "@example.com"));
        ReservaRes r = criar(TOCA, dias(40), dias(43), 2, List.of(), s);
        assertEquals(0, unidadeRepository.unidadesLivres(cavernaId(TOCA), dias(40), dias(43)).size());

        Reserva e = reserva(r.codigo());
        e.setExpiraEm(com.hugabuga.api.service.Util.agora().minusSeconds(5));
        reservaRepository.saveAndFlush(e);

        int n = reservaService.expirarVencidas();
        assertTrue(n >= 1);
        assertEquals(EXPIRADA, reserva(r.codigo()).getStatus());
        assertEquals(1, unidadeRepository.unidadesLivres(cavernaId(TOCA), dias(40), dias(43)).size(),
                "a unidade da reserva expirada volta a ficar livre");
    }

    @Test
    void jobDeExpiracaoNaoMexeEmReservasConfirmadas() {
        Solicitante s = anonimo("cliente@example.com");
        ReservaRes r = criarPadrao(s);
        pagamentoService.pagar(r.codigo(), naChegada(), s); // CONFIRMADA
        Reserva e = reserva(r.codigo());
        e.setExpiraEm(com.hugabuga.api.service.Util.agora().minusMinutes(30));
        reservaRepository.saveAndFlush(e);

        reservaService.expirarVencidas();
        assertEquals(CONFIRMADA, reserva(r.codigo()).getStatus());
    }

    @Test
    void jobDeConclusaoCreditaUmOssoACadaDezReaisEmDiarias() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);                          // diárias R$ 1.560 -> 156 ossos
        pagamentoService.pagar(r.codigo(), naChegada(), s);
        Reserva e = reserva(r.codigo());
        e.setCheckIn(dias(-5));                                 // estadia já terminada
        e.setCheckOut(dias(-2));
        reservaRepository.saveAndFlush(e);
        int saldoAntes = usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos();

        reservaService.concluirEstadias();

        assertEquals(CONCLUIDA, reserva(r.codigo()).getStatus());
        assertEquals(saldoAntes + 156, usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos());
    }
}
