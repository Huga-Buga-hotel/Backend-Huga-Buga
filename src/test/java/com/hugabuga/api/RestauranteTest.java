package com.hugabuga.api;

import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.dto.RestauranteDtos.HorarioRes;
import com.hugabuga.api.dto.RestauranteDtos.MesaReq;
import com.hugabuga.api.dto.RestauranteDtos.MesaRes;
import com.hugabuga.api.model.OrigemMesa;
import com.hugabuga.api.service.RestauranteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O Banquete: salão de 50 lugares, mesa de visitante de fora e mesa de hóspede. */
class RestauranteTest extends IntegrationTestBase {

    @Autowired RestauranteService restauranteService;

    private final LocalDate dia = dias(10);
    private static final LocalTime VINTE = LocalTime.of(20, 0);

    private MesaReq externa(LocalTime horario, int pessoas, String email) {
        return new MesaReq(null, "Visitante", email, "+5511988887777", dia, horario, pessoas, 0, false);
    }

    private void lotarAs20h() { // 4 mesas de 12 = 48 lugares às 20:00
        for (int i = 0; i < 4; i++) restauranteService.reservarMesa(externa(VINTE, 12, "v" + i + "@example.com"), anonimo(null));
    }

    @Test
    void salaoTem50LugaresEOProximoPedidoQueNaoCabeEhRecusado() {
        lotarAs20h();
        var e = erro(() -> restauranteService.reservarMesa(externa(VINTE, 3, "x@example.com"), anonimo(null)));
        assertErro(e, HttpStatus.CONFLICT, "VAGAS_ESGOTADAS");
        assertEquals(2, e.getDados().get("vagasRestantes"));

        assertDoesNotThrow(() -> restauranteService.reservarMesa(externa(VINTE, 2, "y@example.com"), anonimo(null)));
    }

    @Test
    void mesasDeHorariosVizinhosSomamNaLotacao() {
        lotarAs20h();
        // 19:30 + 90 min alcança o salão das 20:00, que só tem 2 lugares
        assertErro(erro(() -> restauranteService.reservarMesa(externa(LocalTime.of(19, 30), 3, "a@example.com"), anonimo(null))),
                HttpStatus.CONFLICT, "VAGAS_ESGOTADAS");
        // 21:30 começa quando a turma das 20:00 já saiu (20:00 + 90 min)
        assertDoesNotThrow(() -> restauranteService.reservarMesa(externa(LocalTime.of(21, 30), 12, "b@example.com"), anonimo(null)));
    }

    @Test
    void horariosMostramLotadoQuandoNaoCabe() {
        lotarAs20h();
        List<HorarioRes> hs = restauranteService.horarios(dia, 3);
        assertEquals("LOTADO", hs.stream().filter(h -> h.horario().equals(VINTE)).findFirst().orElseThrow().status());
        assertEquals("LIVRE", hs.stream().filter(h -> h.horario().equals(LocalTime.of(21, 30))).findFirst().orElseThrow().status());
    }

    @Test
    void visitanteDeForaReservaSemEstadiaESemDesconto() {
        MesaRes m = restauranteService.reservarMesa(externa(VINTE, 4, "Fora@Example.com"), anonimo(null));
        assertEquals(OrigemMesa.EXTERNO, m.origem());
        assertNull(m.codigoReserva());
        assertEquals(0, m.descontoHospedePct());

        // dono pelo e-mail de contato (sem diferenciar maiúsculas); outro e-mail não vê
        assertDoesNotThrow(() -> restauranteService.detalhe(m.codigo(), anonimo("fora@example.com")));
        assertErro(erro(() -> restauranteService.detalhe(m.codigo(), anonimo("outro@example.com"))),
                HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }

    @Test
    void visitanteDeForaPrecisaDeContato() {
        MesaReq semEmail = new MesaReq(null, "Visitante", null, "+5511988887777", dia, VINTE, 2, 0, false);
        assertErro(erro(() -> restauranteService.reservarMesa(semEmail, anonimo(null))), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void visitanteLogadoUsaOsDadosDaConta() {
        MesaReq soPessoas = new MesaReq(null, null, null, null, dia, VINTE, 2, 0, true);
        MesaRes m = restauranteService.reservarMesa(soPessoas, marina());
        assertEquals(OrigemMesa.EXTERNO, m.origem());
        assertEquals("Marina Souza", m.nomeContato());
    }

    @Test
    void hospedeSoReservaComEstadiaConfirmadaEGanhaDesconto() {
        Solicitante cliente = anonimo("cliente@example.com");
        ReservaRes r = criar(TOCA, dias(9), dias(12), 2, List.of(), cliente);
        MesaReq req = new MesaReq(r.codigo(), null, null, null, dia, VINTE, 2, 0, false);

        assertErro(erro(() -> restauranteService.reservarMesa(req, cliente)), HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");

        pagamentoService.pagar(r.codigo(), naChegada(), cliente);
        MesaRes m = restauranteService.reservarMesa(req, cliente);
        assertEquals(OrigemMesa.HOSPEDE, m.origem());
        assertEquals(r.codigo(), m.codigoReserva());
        assertEquals(15, m.descontoHospedePct());
    }

    @Test
    void horarioForaDaGradeEhRecusado() {
        assertErro(erro(() -> restauranteService.reservarMesa(externa(LocalTime.of(18, 0), 2, "z@example.com"), anonimo(null))),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }
}
