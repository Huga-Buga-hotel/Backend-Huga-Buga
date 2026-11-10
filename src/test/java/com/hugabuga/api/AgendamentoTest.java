package com.hugabuga.api;

import com.hugabuga.api.dto.AtividadeDtos.AgendamentoRes;
import com.hugabuga.api.dto.AtividadeDtos.AgendarReq;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.SessaoAtividade;
import com.hugabuga.api.model.StatusAgendamento;
import com.hugabuga.api.repository.AtividadeRepository;
import com.hugabuga.api.repository.SessaoAtividadeRepository;
import com.hugabuga.api.service.AtividadeService;
import com.hugabuga.api.service.Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Listagem de agendamentos da reserva (o app usa o id para cancelar). */
class AgendamentoTest extends IntegrationTestBase {

    @Autowired AtividadeService atividadeService;
    @Autowired AtividadeRepository atividadeRepository;
    @Autowired SessaoAtividadeRepository sessaoRepository;

    private final Solicitante cliente = anonimo("cliente@example.com");

    /** Uma sessão de "Pintura rupestre" (sem faixa de idade) no dia seguinte ao check-in, com todas as vagas livres. */
    private SessaoAtividade sessaoNaEstadia() {
        var pintura = atividadeRepository.findByAtivaTrue().stream()
                .filter(a -> a.getNome().equals("Pintura rupestre")).findFirst().orElseThrow();
        var inicio = dias(6).atStartOfDay(Util.FUSO).toOffsetDateTime();
        SessaoAtividade s = sessaoRepository.findByAtividadeIdAndInicioBetweenOrderByInicio(pintura.getId(), inicio, inicio.plusDays(1))
                .getFirst();
        s.setVagasOcupadas(0); // a ocupação do seed varia por dia
        return sessaoRepository.saveAndFlush(s);
    }

    @Test
    void listaOsAgendamentosComIdEPermiteCancelar() {
        ReservaRes r = criar(TOCA, dias(5), dias(8), 2, List.of(), cliente);
        pagamentoService.pagar(r.codigo(), naChegada(), cliente);
        SessaoAtividade sessao = sessaoNaEstadia();

        AgendamentoRes criado = atividadeService.agendar(r.codigo(), new AgendarReq(sessao.getId(), 2), cliente);
        List<AgendamentoRes> lista = atividadeService.listarAgendamentos(r.codigo(), cliente);

        assertEquals(1, lista.size());
        assertEquals(criado.id(), lista.getFirst().id());
        assertEquals("Pintura rupestre", lista.getFirst().atividade());
        assertEquals(2 * 4500, lista.getFirst().valorCentavos());

        atividadeService.cancelar(r.codigo(), lista.getFirst().id(), cliente);
        assertEquals(StatusAgendamento.CANCELADO, atividadeService.listarAgendamentos(r.codigo(), cliente).getFirst().status());
    }

    @Test
    void outraPessoaNaoVeOsAgendamentos() {
        ReservaRes r = criarPadrao(cliente);
        var e = erro(() -> atividadeService.listarAgendamentos(r.codigo(), anonimo("intruso@example.com")));
        assertErro(e, HttpStatus.FORBIDDEN, "SEM_PERMISSAO");
    }
}
