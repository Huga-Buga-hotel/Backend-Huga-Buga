package com.hugabuga.api;

import com.hugabuga.api.dto.CavernaDtos.DiaDisponibilidadeRes;
import com.hugabuga.api.dto.CavernaDtos.FiltrosRes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tempo de estadia (mínimo/máximo de noites e antecedência) e o calendário de disponibilidade. */
class EstadiaTest extends IntegrationTestBase {

    @Test
    void estadiaDe30NoitesPodeMasDe31Nao() {
        assertEquals(30, cavernaService.cotar(cavernaId(TOCA), dias(10), dias(40), 2, List.of()).noites());
        assertErro(erro(() -> cavernaService.cotar(cavernaId(TOCA), dias(10), dias(41), 2, List.of())),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void reservaAbreComAte365DiasDeAntecedencia() {
        assertDoesNotThrow(() -> cavernaService.cotar(cavernaId(TOCA), dias(365), dias(367), 2, List.of()));
        assertErro(erro(() -> cavernaService.cotar(cavernaId(TOCA), dias(366), dias(368), 2, List.of())),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void calendarioMostraTocasLivresPorNoite() {
        criar(TOCA, dias(20), dias(22), 2, List.of(), anonimo("a@example.com")); // noites 20 e 21
        int total = tocas(TOCA);

        List<DiaDisponibilidadeRes> cal = cavernaService.disponibilidade(cavernaId(TOCA), dias(19), dias(22));

        assertEquals(4, cal.size());
        assertEquals(total, cal.get(0).unidadesLivres());       // dia 19
        assertEquals(total - 1, cal.get(1).unidadesLivres());   // dia 20
        assertEquals(total - 1, cal.get(2).unidadesLivres());   // dia 21
        assertEquals(total, cal.get(3).unidadesLivres(), "a noite do check-out já está livre");
        assertTrue(cal.stream().allMatch(DiaDisponibilidadeRes::disponivel));
        assertEquals(52000, cal.getFirst().precoNoiteCentavos());
    }

    @Test
    void calendarioSemTocaLivreMarcaIndisponivel() {
        for (int i = 0; i < tocas(TOCA); i++) criar(TOCA, dias(20), dias(21), 2, List.of(), anonimo("t" + i + "@example.com"));
        DiaDisponibilidadeRes dia = cavernaService.disponibilidade(cavernaId(TOCA), dias(20), dias(20)).getFirst();
        assertFalse(dia.disponivel());
        assertEquals(0, dia.unidadesLivres());
    }

    @Test
    void calendarioTemLimiteDe92Dias() {
        assertErro(erro(() -> cavernaService.disponibilidade(cavernaId(TOCA), dias(1), dias(100))),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void filtrosTrazemAsCincoSuitesEAsRegrasDeEstadia() {
        FiltrosRes f = cavernaService.filtros();
        assertEquals(5, f.categorias().stream().mapToLong(c -> c.quantidade()).sum());
        assertEquals(1, f.minNoites());
        assertEquals(30, f.maxNoites());
        assertEquals(39000, f.precoMinCentavos());
        assertEquals(98000, f.precoMaxCentavos());
    }
}
