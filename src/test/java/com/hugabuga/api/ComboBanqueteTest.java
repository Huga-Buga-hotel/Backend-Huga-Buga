package com.hugabuga.api;

import com.hugabuga.api.dto.ReservaDtos.*;
import com.hugabuga.api.dto.RestauranteDtos.MesaReq;
import com.hugabuga.api.model.OrigemMesa;
import com.hugabuga.api.model.Reserva;
import com.hugabuga.api.model.ReservaMesa;
import com.hugabuga.api.model.StatusMesa;
import com.hugabuga.api.service.RestauranteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Combo quarto + Banquete: preço, mesas por noite, lotação, confirmação, expiração e cancelamento. */
class ComboBanqueteTest extends IntegrationTestBase {

    @Autowired RestauranteService restauranteService;

    private final Solicitante cliente = anonimo("cliente@example.com");
    private static final LocalTime VINTE = LocalTime.of(20, 0);

    /** Toca do Mamute, 3 noites, 2 adultos + crianças de 9 e 4 anos. */
    private ReservaRes reservaComCriancas() { return criar(TOCA, dias(30), dias(33), 2, List.of(9, 4), cliente); }

    private List<ReservaMesa> mesasDoCombo(String codigo) {
        return mesaRepository.findByReservaIdAndOrigem(reserva(codigo).getId(), OrigemMesa.COMBO);
    }

    @Test
    void comboCobraAdultoECriancaDe6A12PorNoiteEReservaUmaMesaPorNoite() {
        ReservaRes r = reservaComCriancas();
        r = reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);

        // 3 noites x (2 adultos x R$ 89 + 1 criança de 9 anos x R$ 45); a de 4 anos não paga
        assertEquals(3 * (2 * 8900 + 4500), r.valorCombo());
        assertEquals(156000 + 66900, r.total());
        assertEquals(3, r.combo().mesas().size());
        assertEquals(2, r.combo().adultosPagantes());
        assertEquals(1, r.combo().criancasPagantes());

        List<ReservaMesa> mesas = mesasDoCombo(r.codigo());
        assertTrue(mesas.stream().allMatch(m -> m.getStatus() == StatusMesa.PENDENTE && m.getPessoas() == 4));
    }

    @Test
    void cupomTambemValeSobreOCombo() {
        ReservaRes r = reservaComCriancas();
        reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);
        r = reservaService.aplicarCupom(r.codigo(), new CupomReq("UGABUGA10"), cliente);
        assertEquals(22290, r.desconto()); // 10% de 222.900
    }

    @Test
    void pagamentoConfirmaAsMesasDoCombo() {
        ReservaRes r = reservaComCriancas();
        reservaService.definirCombo(r.codigo(), new ComboReq("BANQUETE_COMPLETO", VINTE), cliente);
        pagamentoService.pagar(r.codigo(), cartao(CARTAO_APROVADO, 1), cliente);
        assertTrue(mesasDoCombo(r.codigo()).stream().allMatch(m -> m.getStatus() == StatusMesa.CONFIRMADA));
    }

    @Test
    void trocarOuTirarOComboNaoDeixaMesasSobrando() {
        ReservaRes r = reservaComCriancas();
        reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);
        reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", LocalTime.of(21, 0)), cliente);
        assertEquals(3, mesasDoCombo(r.codigo()).stream().filter(m -> m.getStatus() != StatusMesa.CANCELADA).count());

        r = reservaService.removerCombo(r.codigo(), cliente);
        assertNull(r.combo());
        assertEquals(156000, r.total());
        assertTrue(mesasDoCombo(r.codigo()).stream().allMatch(m -> m.getStatus() == StatusMesa.CANCELADA));
    }

    @Test
    void comboExigeQuemVemPreenchido() {
        ReservaRes r = reservaService.criar(new CriarReservaReq(cavernaId(TOCA), dias(30), dias(33), 2, List.of(),
                "cliente@example.com", null), cliente);
        assertErro(erro(() -> reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente)),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void comboRespeitaALotacaoDoSalao() {
        for (int i = 0; i < 4; i++) { // 48 lugares às 20:00 na segunda noite
            restauranteService.reservarMesa(new MesaReq(null, "V", "v" + i + "@example.com", "+5511900000000",
                    dias(31), VINTE, 12, 0, false), anonimo(null));
        }
        ReservaRes r = reservaComCriancas(); // 4 pessoas
        var e = erro(() -> reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente));
        assertErro(e, HttpStatus.CONFLICT, "VAGAS_ESGOTADAS");
        assertTrue(mesasDoCombo(r.codigo()).isEmpty(), "nenhuma noite fica reservada pela metade");
    }

    @Test
    void reservaExpiradaLiberaOsLugaresDoCombo() {
        ReservaRes r = reservaComCriancas();
        reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);
        Reserva e = reserva(r.codigo());
        e.setExpiraEm(com.hugabuga.api.service.Util.agora().minusSeconds(5));
        reservaRepository.saveAndFlush(e);

        reservaService.expirarVencidas();

        assertTrue(mesasDoCombo(r.codigo()).stream().allMatch(m -> m.getStatus() == StatusMesa.CANCELADA));
    }

    @Test
    void cancelarAEstadiaCancelaOCombo() {
        ReservaRes r = reservaComCriancas();
        reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);
        pagamentoService.pagar(r.codigo(), naChegada(), cliente);

        reservaService.cancelar(r.codigo(), cliente);

        assertTrue(mesasDoCombo(r.codigo()).stream().allMatch(m -> m.getStatus() == StatusMesa.CANCELADA));
    }

    @Test
    void mesaDoComboNaoSaiSozinha() {
        ReservaRes r = reservaComCriancas();
        r = reservaService.definirCombo(r.codigo(), new ComboReq("JANTAR_DA_TRIBO", VINTE), cliente);
        pagamentoService.pagar(r.codigo(), naChegada(), cliente);
        String mesa = r.combo().mesas().getFirst();
        assertErro(erro(() -> restauranteService.cancelar(mesa, cliente)), HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }
}
