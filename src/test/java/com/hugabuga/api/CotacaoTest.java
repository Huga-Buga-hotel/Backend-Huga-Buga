package com.hugabuga.api;

import com.hugabuga.api.dto.CavernaDtos.CotacaoRes;
import com.hugabuga.api.model.TarifaEspecial;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Teste 2: adulto extra, criança até 5 anos, criança de 13 virando adulto, tarifa de feriado. */
class CotacaoTest extends IntegrationTestBase {

    private CotacaoRes cotar(int adultos, List<Integer> idades) {
        return cavernaService.cotar(cavernaId(TOCA), dias(50), dias(53), adultos, idades);
    }

    @Test
    void doisAdultosPagamSoADiaria() {
        CotacaoRes c = cotar(2, List.of());
        assertEquals(3, c.noites());
        assertEquals(0, c.adultosExtras());
        assertEquals(156000, c.valorDiariasCentavos());
        assertFalse(c.feriado());
    }

    @Test
    void cadaAdultoAMaisSoma60PorNoite() {
        CotacaoRes c = cotar(3, List.of());
        assertEquals(1, c.adultosExtras());
        assertEquals(156000 + 3 * 6000, c.valorDiariasCentavos()); // R$ 1.740,00
    }

    @Test
    void criancaDeAte5AnosNaoPagaNada() {
        CotacaoRes c = cotar(2, List.of(5));
        assertEquals(1, c.criancasGratis());
        assertEquals(156000, c.valorDiariasCentavos());
    }

    @Test
    void criancaDe6A12AnosNaoSomaAdicional() {
        CotacaoRes c = cotar(2, List.of(6, 12));
        assertEquals(0, c.criancasGratis());
        assertEquals(0, c.adultosExtras());
        assertEquals(156000, c.valorDiariasCentavos());
    }

    @Test
    void criancaDe13AnosContaComoAdulto() {
        CotacaoRes c = cotar(2, List.of(13));
        assertEquals(3, c.adultosCobrados());
        assertEquals(1, c.adultosExtras());
        assertEquals(174000, c.valorDiariasCentavos()); // o total sobe R$ 60/noite
    }

    @Test
    void tarifaDeFeriadoSubstituiOPrecoBaseNaqueleDia() {
        TarifaEspecial t = new TarifaEspecial();
        t.setCavernaId(cavernaId(TOCA));
        t.setData(dias(51));          // a noite do meio
        t.setPrecoCentavos(65000);
        t.setMotivo("Feriado de teste");
        tarifaRepository.saveAndFlush(t);

        CotacaoRes c = cotar(2, List.of());
        assertTrue(c.feriado());
        assertEquals(52000 + 65000 + 52000, c.valorDiariasCentavos());
        assertEquals(65000, c.detalhe().get(1).precoNoiteCentavos());
    }

    @Test
    void capacidadeSoContaPessoasAcimaDe5Anos() {
        // capacidade 5: 5 adultos + 2 crianças pequenas cabem
        CotacaoRes c = cotar(5, List.of(4, 3));
        assertEquals(2, c.criancasGratis());
        assertEquals(3, c.adultosExtras());
        assertEquals(156000 + 3 * 6000 * 3, c.valorDiariasCentavos());
    }

    @Test
    void acimaDaCapacidadeDa400() {
        var e = erro(() -> cotar(6, List.of()));
        assertErro(e, HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void datasInvalidasSaoRecusadas() {
        UUIDHolder id = new UUIDHolder(cavernaId(TOCA));
        assertErro(erro(() -> cavernaService.cotar(id.v, dias(50), dias(50), 2, List.of())), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> cavernaService.cotar(id.v, dias(53), dias(50), 2, List.of())), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> cavernaService.cotar(id.v, dias(-1), dias(2), 2, List.of())), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void idadeDeCriancaForaDeIntervaloEhRecusada() {
        assertErro(erro(() -> cotar(2, List.of(18))), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> cotar(2, List.of(-1))), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    private record UUIDHolder(java.util.UUID v) {}
}
