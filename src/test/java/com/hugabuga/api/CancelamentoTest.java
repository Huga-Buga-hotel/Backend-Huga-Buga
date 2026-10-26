package com.hugabuga.api;

import com.hugabuga.api.dto.ReservaDtos.CancelarRes;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Teste 6: cancelamento dentro e fora do prazo de 2 dias. */
class CancelamentoTest extends IntegrationTestBase {

    private final Solicitante cliente = anonimo("cliente@example.com");

    private int saldoMarina() { return usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos(); }

    // ---------- dentro do prazo: grátis, reembolso integral, ossos voltam ----------

    @Test
    void dentroDoPrazoReembolsaTudoEDevolveOsOssos() {
        Solicitante s = marina();
        ReservaRes r = criar(TOCA, dias(30), dias(33), 2, List.of(), s);
        reservaService.usarOssos(r.codigo(), 100, s);
        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), s);
        assertEquals(350, saldoMarina());                       // 450 - 100

        CancelarRes c = reservaService.cancelar(r.codigo(), s);

        assertEquals(StatusReserva.CANCELADA, c.reserva().status());
        assertEquals(155000, c.reembolsoCentavos());            // R$ 1.560 - R$ 10 de ossos
        assertEquals(0, c.multaCentavos());
        assertEquals(450, saldoMarina(), "os ossos usados voltam ao saldo");
        assertEquals(520, movimentoRepository.somaGanhos(marinaId()), "devolver ossos não conta como ganho (nível não sobe)");
        assertNotNull(reserva(r.codigo()).getCanceladaEm());
    }

    @Test
    void comExatamente2DiasDeAntecedenciaAindaEhGratis() {
        ReservaRes r = criar(TOCA, dias(2), dias(4), 2, List.of(), cliente);
        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente);

        CancelarRes c = reservaService.cancelar(r.codigo(), cliente);
        assertEquals(104000, c.reembolsoCentavos());
        assertEquals(0, c.multaCentavos());
    }

    @Test
    void cancelarLiberaATocaParaOutrosHospedes() {
        for (int i = 0; i < 2; i++) criar(TOCA, dias(30), dias(33), 2, List.of(), anonimo("x" + i + "@example.com"));
        ReservaRes r = criar(TOCA, dias(30), dias(33), 2, List.of(), cliente);
        assertEquals(0, unidadeRepository.unidadesLivres(cavernaId(TOCA), dias(30), dias(33)).size());

        reservaService.cancelar(r.codigo(), cliente);

        assertEquals(1, unidadeRepository.unidadesLivres(cavernaId(TOCA), dias(30), dias(33)).size());
    }

    @Test
    void reservaAindaPendenteCancelaSemReembolsoNemMulta() {
        ReservaRes r = criarPadrao(cliente);
        CancelarRes c = reservaService.cancelar(r.codigo(), cliente);
        assertEquals(StatusReserva.CANCELADA, c.reserva().status());
        assertEquals(0, c.reembolsoCentavos());
        assertEquals(0, c.multaCentavos());
    }

    @Test
    void naChegadaDentroDoPrazoNaoGeraMulta() {
        ReservaRes r = criarPadrao(cliente);
        pagamentoService.pagar(r.codigo(), naChegada(), cliente);

        CancelarRes c = reservaService.cancelar(r.codigo(), cliente);
        assertEquals(0, c.multaCentavos());
        assertEquals(0, c.reembolsoCentavos(), "nada havia sido pago");
    }

    // ---------- fora do prazo: sem reembolso ----------

    @Test
    void foraDoPrazoNaoReembolsaNemDevolveOssos() {
        Solicitante s = marina();
        ReservaRes r = criar(TOCA, dias(1), dias(3), 2, List.of(), s);   // check-in amanhã
        reservaService.usarOssos(r.codigo(), 100, s);
        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), s);
        assertEquals(350, saldoMarina());

        CancelarRes c = reservaService.cancelar(r.codigo(), s);

        assertEquals(StatusReserva.CANCELADA, c.reserva().status());
        assertEquals(0, c.reembolsoCentavos());
        assertEquals(350, saldoMarina(), "fora do prazo os ossos não voltam");
    }

    @Test
    void foraDoPrazoComPagamentoNaChegadaGeraMultaDeUmaDiaria() {
        ReservaRes r = criar(TOCA, dias(1), dias(3), 2, List.of(), cliente);    // 2 noites = R$ 1.040
        pagamentoService.pagar(r.codigo(), naChegada(), cliente);

        CancelarRes c = reservaService.cancelar(r.codigo(), cliente);

        assertEquals(52000, c.multaCentavos());                  // 1 diária
        assertEquals(0, c.reembolsoCentavos());
        List<Pagamento> pagamentos = pagamentoRepository.findByReservaId(reserva(r.codigo()).getId());
        assertTrue(pagamentos.stream().anyMatch(p -> p.getMetodo() == MetodoPagamento.NA_CHEGADA
                && p.getStatus() == StatusPagamento.PENDENTE && p.getValorCentavos() == 52000), "multa pendente na chegada");
        assertTrue(pagamentos.stream().anyMatch(p -> p.getMetodo() == MetodoPagamento.NA_CHEGADA
                && p.getStatus() == StatusPagamento.EXPIRADO), "a cobrança original deixa de valer");
    }

    // ---------- estados e autorização ----------

    @Test
    void naoDaParaCancelarDuasVezes() {
        ReservaRes r = criarPadrao(cliente);
        reservaService.cancelar(r.codigo(), cliente);
        var e = erro(() -> reservaService.cancelar(r.codigo(), cliente));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }

    @Test
    void naoDaParaCancelarReservaDeOutraPessoa() {
        ReservaRes r = criarPadrao(cliente);
        var e = erro(() -> reservaService.cancelar(r.codigo(), anonimo("intruso@example.com")));
        assertErro(e, HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
        assertEquals(StatusReserva.PENDENTE, reserva(r.codigo()).getStatus());
    }
}
