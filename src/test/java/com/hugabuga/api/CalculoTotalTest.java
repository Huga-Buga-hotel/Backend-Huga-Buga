package com.hugabuga.api;

import com.hugabuga.api.dto.ReservaDtos.*;
import com.hugabuga.api.model.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Teste 1: diárias + extras − cupom − ossos, parcelas e Pix (exemplo do guia). */
class CalculoTotalTest extends IntegrationTestBase {

    @Test
    void exemploDoGuia_diariasExtrasCupomParcelaEPix() {
        Solicitante s = marina();
        ReservaRes r = criar(TOCA, dias(60), dias(63), 2, List.of(), s);
        assertEquals(156000, r.valorDiarias());          // 3 x R$ 520
        assertEquals(156000, r.total());

        // extras: late check-out (80) + fogueira VIP (150) = R$ 230
        r = reservaService.salvarExtras(r.codigo(), new ExtrasReq(List.of(
                new ExtraItemReq("LATE_CHECKOUT", 1), new ExtraItemReq("FOGUEIRA_VIP", 1))), s);
        assertEquals(23000, r.valorExtras());
        assertEquals(179000, r.total());
        assertTrue(r.lateCheckout());

        // cupom 10% sobre diárias + extras
        r = reservaService.aplicarCupom(r.codigo(), new CupomReq("UGABUGA10"), s);
        assertEquals(17900, r.desconto());
        assertEquals(161100, r.total());
        assertEquals("UGABUGA10", r.cupom());

        // parcela e Pix (o desconto do Pix vem por último, só no valor do pagamento)
        assertEquals(53700, reservaService.valorParcela(161100, 3));
        assertEquals(153045, reservaService.valorComPix(161100));
    }

    @Test
    void removerCupomVoltaAoTotalAnterior() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        r = reservaService.aplicarCupom(r.codigo(), new CupomReq("ugabuga10"), s); // sem diferenciar maiúsculas
        assertEquals(140400, r.total());
        r = reservaService.removerCupom(r.codigo(), s);
        assertEquals(156000, r.total());
        assertNull(r.cupom());
    }

    @Test
    void ossosAbatemDoTotalMasSoSaemDoSaldoNaConfirmacao() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        r = reservaService.usarOssos(r.codigo(), 100, s);      // 100 ossos = R$ 10
        assertEquals(100, r.ossosUsados());
        assertEquals(155000, r.total());
        assertEquals(450, usuarioRepository.findById(marinaId()).orElseThrow().getSaldoOssos()); // ainda não debitou

        r = reservaService.desfazerOssos(r.codigo(), s);
        assertEquals(0, r.ossosUsados());
        assertEquals(156000, r.total());
    }

    @Test
    void ossosCobremNoMaximo50PorCentoDoTotal() {
        Usuario u = usuarioRepository.findById(marinaId()).orElseThrow();
        u.setSaldoOssos(100_000);
        usuarioRepository.saveAndFlush(u);

        Solicitante s = marina();
        ReservaRes criada = criarPadrao(s); // R$ 1.560,00 -> limite de R$ 780,00 = 7.800 ossos

        var e = erro(() -> reservaService.usarOssos(criada.codigo(), 8000, s));
        assertErro(e, HttpStatus.BAD_REQUEST, "VALIDACAO");

        ReservaRes r = reservaService.usarOssos(criada.codigo(), 7800, s);
        assertEquals(78000, r.total());
    }

    @Test
    void ossosSoEmMultiplosDeDezEAteOSaldo() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        assertErro(erro(() -> reservaService.usarOssos(r.codigo(), 15, s)), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> reservaService.usarOssos(r.codigo(), 460, s)), HttpStatus.BAD_REQUEST, "VALIDACAO"); // saldo é 450
    }

    @Test
    void cupomInexistenteDa422() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        assertErro(erro(() -> reservaService.aplicarCupom(r.codigo(), new CupomReq("NAOEXISTE"), s)),
                HttpStatus.UNPROCESSABLE_ENTITY, "CUPOM_INVALIDO");
    }

    @Test
    void regraDeArredondamentoDeParcelasEPix() {
        assertEquals(33, reservaService.valorParcela(100, 3));   // 33,33 -> 33
        assertEquals(34, reservaService.valorParcela(101, 3));   // 33,67 -> 34
        assertEquals(95, reservaService.valorComPix(100));       // -5%
        assertEquals(100, reservaService.valorParcela(100, 1));
    }
}
