package com.hugabuga.api;

import com.hugabuga.api.dto.PagamentoDtos.PagamentoReq;
import com.hugabuga.api.dto.PagamentoDtos.PagamentoRes;
import com.hugabuga.api.dto.ReservaDtos.CupomReq;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Teste 5: finais 0002, 0069 e um cartão aprovado (mais Pix e "na chegada"). */
class PagamentoSimuladoTest extends IntegrationTestBase {

    private final Solicitante cliente = anonimo("cliente@example.com");

    private int usosDoCupom() { return cupomRepository.findByCodigoIgnoreCase("UGABUGA10").orElseThrow().getUsos(); }

    private ReservaRes reservaComCupom(Solicitante s) {
        ReservaRes r = criarPadrao(s);
        return reservaService.aplicarCupom(r.codigo(), new CupomReq("UGABUGA10"), s); // total R$ 1.404,00
    }

    // ---------- cartão recusado ----------

    @Test
    void finalSaldoInsuficienteRecusaSemMexerEmNada() {
        ReservaRes r = reservaComCupom(cliente);

        var e = erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_SALDO_INSUFICIENTE, 1), cliente));

        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "PAGAMENTO_RECUSADO");
        assertTrue(e.getMessage().contains("Nada foi cobrado"));
        assertEquals("SALDO_INSUFICIENTE", e.getDados().get("motivo"));
        assertEquals("0002", e.getDados().get("cartaoFinal"));

        Reserva depois = reserva(r.codigo());
        assertEquals(StatusReserva.PENDENTE, depois.getStatus(), "recusa não muda o estado");
        assertEquals(r.expiraEm(), depois.getExpiraEm(), "recusa não mexe no cronômetro");
        assertEquals(0, usosDoCupom(), "recusa não consome o cupom");

        List<Pagamento> tentativas = pagamentoRepository.findByReservaId(depois.getId());
        assertEquals(1, tentativas.size(), "a tentativa recusada fica registrada");
        assertEquals(StatusPagamento.RECUSADO, tentativas.get(0).getStatus());
        assertEquals("0002", tentativas.get(0).getCartaoFinal());
    }

    @Test
    void finalCartaoExpiradoRecusa() {
        ReservaRes r = criarPadrao(cliente);
        var e = erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_EXPIRADO, 1), cliente));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "PAGAMENTO_RECUSADO");
        assertEquals("CARTAO_EXPIRADO", e.getDados().get("motivo"));
    }

    @Test
    void depoisDeRecusarDaParaTentarOutroCartao() {
        ReservaRes r = criarPadrao(cliente);
        erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_SALDO_INSUFICIENTE, 1), cliente));

        PagamentoRes ok = pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente);
        assertEquals(StatusPagamento.APROVADO, ok.status());
        assertEquals(StatusReserva.CONFIRMADA, reserva(r.codigo()).getStatus());
    }

    // ---------- cartão aprovado ----------

    @Test
    void cartaoValidoAprovaNaHoraEConfirmaAReserva() {
        ReservaRes r = reservaComCupom(cliente);

        PagamentoRes p = pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 3), cliente);

        assertEquals(StatusPagamento.APROVADO, p.status());
        assertEquals(MetodoPagamento.CARTAO, p.metodo());
        assertEquals("1111", p.cartaoFinal());
        assertEquals("VISA", p.cartaoBandeira());
        assertEquals(3, p.parcelas());
        assertEquals(140400, p.valorCentavos());
        assertEquals(46800, p.valorParcelaCentavos());      // 3x sem juros

        assertEquals(StatusReserva.CONFIRMADA, reserva(r.codigo()).getStatus());
        assertEquals(1, usosDoCupom(), "o cupom só é consumido na confirmação");
        assertTrue(reservaService.voucher(r.codigo(), cliente).pago());
    }

    @Test
    void clicarDuasVezesEmPagarNaoCriaDuasCobrancas() {
        ReservaRes r = criarPadrao(cliente);
        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente);

        var e = erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");

        long aprovados = pagamentoRepository.findByReservaId(reserva(r.codigo()).getId()).stream()
                .filter(p -> p.getStatus() == StatusPagamento.APROVADO).count();
        assertEquals(1, aprovados);
    }

    @Test
    void cartaoComNumeroInvalidoNoLuhnDa400ENaoRegistraTentativa() {
        ReservaRes r = criarPadrao(cliente);
        var e = erro(() -> pagamentoService.pagar(r.codigo(), cartao("4111111111111112", 1), cliente));
        assertErro(e, HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertTrue(pagamentoRepository.findByReservaId(reserva(r.codigo()).getId()).isEmpty());
    }

    @Test
    void cartaoExigeAceiteDasRegrasEParcelasDe1A6() {
        ReservaRes r = criarPadrao(cliente);
        PagamentoReq semAceite = new PagamentoReq(MetodoPagamento.CARTAO, 1, CARTAO_APROVADO, "12/30", "123", false);
        assertErro(erro(() -> pagamentoService.pagar(r.codigo(), semAceite, cliente)), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 7), cliente)), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 0), cliente)), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void numeroCompletoDoCartaoNuncaEhGravado() {
        ReservaRes r = criarPadrao(cliente);
        PagamentoRes p = pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente);
        Pagamento gravado = pagamentoRepository.findById(p.id()).orElseThrow();
        assertEquals("1111", gravado.getCartaoFinal());
        assertFalse(gravado.toString().contains(CARTAO_APROVADO));
        assertFalse(p.toString().contains(CARTAO_APROVADO));
    }

    // ---------- ossos ----------

    @Test
    void recusaNaoDebitaOssosEAprovacaoDebita() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        reservaService.usarOssos(r.codigo(), 100, s);

        erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_SALDO_INSUFICIENTE, 1), s));
        assertEquals(450, usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos());
        assertEquals(100, reserva(r.codigo()).getOssosUsados());

        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), s);
        assertEquals(350, usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos());
        MovimentoOssos m = movimentoRepository.findByUsuarioIdOrderByCriadoEmDesc(marinaId()).get(0);
        assertEquals(-100, m.getQuantidade());
        assertEquals(MotivoOssos.RESGATE, m.getMotivo());
    }

    // ---------- Pix ----------

    @Test
    void pixTem5PorCentoDeDescontoEFicaAguardandoPagamento() {
        ReservaRes r = reservaComCupom(cliente);

        PagamentoRes p = pagamentoService.pagar(r.codigo(), pix(), cliente);

        assertEquals(StatusPagamento.PENDENTE, p.status());
        assertEquals(reservaService.valorComPix(140400), p.valorCentavos());
        assertEquals(133380, p.valorCentavos());
        assertNotNull(p.pixCopiaCola());
        assertTrue(p.pixExpiraEm().isAfter(com.hugabuga.api.service.Util.agora()));
        assertEquals(StatusReserva.AGUARDANDO_PAGAMENTO, reserva(r.codigo()).getStatus());
        assertEquals(140400, reserva(r.codigo()).getTotal(), "o desconto do Pix vale só no pagamento, não no total da reserva");
    }

    @Test
    void pedirPixDeNovoDevolveOMesmoEBloqueiaOutroMetodo() {
        ReservaRes r = criarPadrao(cliente);
        PagamentoRes p1 = pagamentoService.pagar(r.codigo(), pix(), cliente);
        PagamentoRes p2 = pagamentoService.pagar(r.codigo(), pix(), cliente);
        assertEquals(p1.id(), p2.id());

        var e = erro(() -> pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }

    @Test
    void confirmacaoSimuladaDoPixConfirmaAReserva() {
        ReservaRes r = reservaComCupom(cliente);
        PagamentoRes p = pagamentoService.pagar(r.codigo(), pix(), cliente);

        PagamentoRes depois = pagamentoService.simularConfirmacao(p.id(), cliente);

        assertEquals(StatusPagamento.APROVADO, depois.status());
        assertEquals(StatusReserva.CONFIRMADA, reserva(r.codigo()).getStatus());
        assertEquals(1, usosDoCupom());
    }

    @Test
    void pixVencidoExpiraOPagamentoEAReserva() {
        ReservaRes r = criarPadrao(cliente);
        PagamentoRes p = pagamentoService.pagar(r.codigo(), pix(), cliente);
        Pagamento gravado = pagamentoRepository.findById(p.id()).orElseThrow();
        gravado.setPixExpiraEm(com.hugabuga.api.service.Util.agora().minusSeconds(5));
        pagamentoRepository.saveAndFlush(gravado);

        int n = pagamentoService.expirarPixVencidos();

        assertTrue(n >= 1);
        assertEquals(StatusPagamento.EXPIRADO, pagamentoRepository.findById(p.id()).orElseThrow().getStatus());
        assertEquals(StatusReserva.EXPIRADA, reserva(r.codigo()).getStatus());
        // e não dá mais para confirmar um Pix vencido
        var e = erro(() -> pagamentoService.simularConfirmacao(p.id(), cliente));
        assertErro(e, HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }

    // ---------- na chegada ----------

    @Test
    void naChegadaConfirmaSemCobrarEMarcaComoNaoPago() {
        ReservaRes r = criarPadrao(cliente);

        PagamentoRes p = pagamentoService.pagar(r.codigo(), naChegada(), cliente);

        assertEquals(MetodoPagamento.NA_CHEGADA, p.metodo());
        assertEquals(StatusPagamento.PENDENTE, p.status());
        assertEquals(156000, p.valorCentavos());
        assertEquals(StatusReserva.CONFIRMADA, reserva(r.codigo()).getStatus());
        assertFalse(reservaService.voucher(r.codigo(), cliente).pago());
    }
}
