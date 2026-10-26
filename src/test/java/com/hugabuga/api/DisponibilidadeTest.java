package com.hugabuga.api;

import com.hugabuga.api.dto.CavernaDtos.BuscaFiltro;
import com.hugabuga.api.dto.CavernaDtos.BuscaRes;
import com.hugabuga.api.dto.CavernaDtos.CavernaCardRes;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.model.CategoriaCaverna;
import com.hugabuga.api.model.Reserva;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.model.UnidadeCaverna;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Teste 3: duas reservas nas mesmas datas na mesma unidade; saída e entrada no mesmo dia. */
class DisponibilidadeTest extends IntegrationTestBase {

    private final LocalDate in = dias(40), out = dias(43);

    private BuscaFiltro busca(List<CategoriaCaverna> categoria) {
        return new BuscaFiltro(in, out, 2, List.of(), categoria, null, null, null, null, null, 0, 10);
    }

    private Reserva reservaCrua(String codigo, UUID cavernaId, UUID unidadeId, LocalDate de, LocalDate ate, StatusReserva status) {
        Reserva r = new Reserva();
        r.setCodigo(codigo);
        r.setCavernaId(cavernaId);
        r.setUnidadeId(unidadeId);
        r.setCheckIn(de);
        r.setCheckOut(ate);
        r.setAdultos(1);
        r.setStatus(status);
        r.setExpiraEm(com.hugabuga.api.service.Util.agora().plusMinutes(15));
        r.setCriadoEm(com.hugabuga.api.service.Util.agora());
        return r;
    }

    @Test
    void cadaReservaPegaUmaTocaDiferenteEAQuartaFicaSemDisponibilidade() {
        for (int i = 0; i < 3; i++) criar(TOCA, in, out, 2, List.of(), anonimo("t" + i + "@example.com"));

        var e = erro(() -> criar(TOCA, in, out, 2, List.of(), anonimo("t4@example.com")));
        assertErro(e, HttpStatus.CONFLICT, "SEM_DISPONIBILIDADE");
        assertNotNull(e.getDados().get("sugestoes"), "deve devolver datas sugeridas, nunca um erro genérico");
    }

    @Test
    void asTresReservasUsamUnidadesDistintas() {
        List<String> unidades = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            unidades.add(criar(TOCA, in, out, 2, List.of(), anonimo("t" + i + "@example.com")).unidadeCodigo());
        }
        assertEquals(3, unidades.stream().distinct().count());
        assertTrue(unidades.containsAll(List.of("T-07", "T-08", "T-09")));
    }

    @Test
    void buscaMostraUltimasQuandoRestam2OuMenos() {
        criar(TOCA, in, out, 2, List.of(), anonimo("a@example.com")); // restam 2 de 3
        BuscaRes res = cavernaService.buscar(busca(null));
        CavernaCardRes toca = res.itens().stream().filter(c -> c.slug().equals(TOCA)).findFirst().orElseThrow();
        assertEquals(2, toca.unidadesLivres());
        assertTrue(toca.ultimas(), "selo \"Últimas 2!\"");

        criar(TOCA, in, out, 2, List.of(), anonimo("b@example.com")); // resta 1
        toca = cavernaService.buscar(busca(null)).itens().stream().filter(c -> c.slug().equals(TOCA)).findFirst().orElseThrow();
        assertEquals(1, toca.unidadesLivres());
    }

    @Test
    void cavernaSemTocaLivreSaiDaBuscaESugerePeriodosProximos() {
        for (int i = 0; i < 3; i++) criar(TOCA, in, out, 2, List.of(), anonimo("t" + i + "@example.com"));

        // só a Toca é FAMILIA: nada livre -> itens vazio e sugestões preenchidas
        BuscaRes res = cavernaService.buscar(busca(List.of(CategoriaCaverna.FAMILIA)));
        assertTrue(res.itens().isEmpty());
        assertFalse(res.sugestoes().isEmpty());

        // sem filtro, as outras cavernas continuam aparecendo e a Toca não
        BuscaRes todas = cavernaService.buscar(busca(null));
        assertTrue(todas.itens().stream().noneMatch(c -> c.slug().equals(TOCA)));
        assertFalse(todas.itens().isEmpty());
    }

    @Test
    void saidaDeUmHospedeNaoBloqueiaAEntradaDoProximo() {
        ReservaRes a = criar(TOCA, in, out, 2, List.of(), anonimo("a@example.com"));
        UUID unidadeReservada = reserva(a.codigo()).getUnidadeId();
        UUID caverna = cavernaId(TOCA);

        // nova estadia começando no dia da saída: a mesma unidade está livre
        assertTrue(unidadeRepository.unidadesLivres(caverna, out, out.plusDays(3)).stream()
                .anyMatch(u -> u.getId().equals(unidadeReservada)));
        // estadia terminando no dia da entrada: idem
        assertTrue(unidadeRepository.unidadesLivres(caverna, in.minusDays(3), in).stream()
                .anyMatch(u -> u.getId().equals(unidadeReservada)));
        // sobreposição de verdade: não está livre
        assertTrue(unidadeRepository.unidadesLivres(caverna, in.plusDays(1), out.plusDays(1)).stream()
                .noneMatch(u -> u.getId().equals(unidadeReservada)));
    }

    @Test
    void reservaNoDiaDaSaidaEhAceitaNaMesmaUnidade() {
        UUID caverna = cavernaId(TOCA);
        UnidadeCaverna unidade = unidadeRepository.unidadesLivres(caverna, in, out).get(0);
        reservaRepository.saveAndFlush(reservaCrua("HB-TST1", caverna, unidade.getId(), in, out, StatusReserva.CONFIRMADA));
        assertDoesNotThrow(() -> reservaRepository.saveAndFlush(
                reservaCrua("HB-TST2", caverna, unidade.getId(), out, out.plusDays(2), StatusReserva.PENDENTE)));
    }

    @Test
    void bancoImpedeReservaDuplaNaMesmaUnidade() {
        // última barreira: mesmo ignorando o service, o PostgreSQL recusa a sobreposição
        UUID caverna = cavernaId(TOCA);
        UnidadeCaverna unidade = unidadeRepository.unidadesLivres(caverna, in, out).get(0);
        reservaRepository.saveAndFlush(reservaCrua("HB-TST1", caverna, unidade.getId(), in, out, StatusReserva.CONFIRMADA));

        assertThrows(DataIntegrityViolationException.class, () -> reservaRepository.saveAndFlush(
                reservaCrua("HB-TST2", caverna, unidade.getId(), in.plusDays(1), out.plusDays(1), StatusReserva.PENDENTE)));
    }

    @Test
    void reservaCanceladaNaoBloqueiaMaisAUnidade() {
        UUID caverna = cavernaId(TOCA);
        UnidadeCaverna unidade = unidadeRepository.unidadesLivres(caverna, in, out).get(0);
        Reserva antiga = reservaRepository.saveAndFlush(
                reservaCrua("HB-TST1", caverna, unidade.getId(), in, out, StatusReserva.CONFIRMADA));
        antiga.setStatus(StatusReserva.CANCELADA);
        reservaRepository.saveAndFlush(antiga);

        assertDoesNotThrow(() -> reservaRepository.saveAndFlush(
                reservaCrua("HB-TST2", caverna, unidade.getId(), in, out, StatusReserva.CONFIRMADA)));
    }
}
