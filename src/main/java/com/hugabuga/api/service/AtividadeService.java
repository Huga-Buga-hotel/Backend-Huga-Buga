package com.hugabuga.api.service;

import com.hugabuga.api.dto.AtividadeDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Catálogo, vagas, agendar, cancelar, agenda do dia e alerta de vaga. */
@Service
@RequiredArgsConstructor
public class AtividadeService {

    private static final int CANCELAR_HORAS_ANTES = 4;
    private static final int IDADE_NOMINAL_ADULTO = 18;

    private final AtividadeRepository atividades;
    private final SessaoAtividadeRepository sessoes;
    private final AgendamentoAtividadeRepository agendamentos;
    private final HospedeReservaRepository hospedes;
    private final ReservaMesaRepository mesas;
    private final AlertaVagaRepository alertas;
    private final ReservaService reservaService;

    @Transactional(readOnly = true)
    public CatalogoRes catalogo(CategoriaAtividade categoria) {
        List<Atividade> todas = atividades.findByAtivaTrue();
        Map<String, Long> contagem = todas.stream()
                .collect(Collectors.groupingBy(a -> a.getCategoria().name(), TreeMap::new, Collectors.counting()));
        List<AtividadeRes> itens = todas.stream()
                .filter(a -> categoria == null || a.getCategoria() == categoria)
                .map(this::toRes).toList();
        return new CatalogoRes(itens, contagem);
    }

    @Transactional(readOnly = true)
    public AtividadeRes detalhe(UUID id) {
        return toRes(atividades.findById(id).filter(Atividade::isAtiva).orElseThrow(() -> Util.naoEncontrado("Atividade")));
    }

    @Transactional(readOnly = true)
    public List<SessaoRes> sessoes(UUID atividadeId, LocalDate de, LocalDate ate) {
        if (!atividades.existsById(atividadeId)) throw Util.naoEncontrado("Atividade");
        LocalDate d = de != null ? de : Util.hoje();
        LocalDate a = ate != null ? ate : d.plusDays(7);
        if (a.isBefore(d)) throw Util.validacao("A data final não pode ser anterior à inicial.");
        OffsetDateTime ini = d.atStartOfDay(Util.FUSO).toOffsetDateTime();
        OffsetDateTime fim = a.plusDays(1).atStartOfDay(Util.FUSO).toOffsetDateTime();
        return sessoes.findByAtividadeIdAndInicioBetweenOrderByInicio(atividadeId, ini, fim).stream()
                .map(this::toRes).toList();
    }

    @Transactional
    public AgendamentoRes agendar(String codigo, AgendarReq req, Solicitante s) {
        Reserva r = reservaService.carregarDonoParaEscrita(codigo, s);
        reservaService.exigirStatus(r, StatusReserva.CONFIRMADA); // só hóspede com reserva confirmada

        SessaoAtividade sessao = sessoes.findById(req.sessaoId()).orElseThrow(() -> Util.naoEncontrado("Sessão"));
        Atividade atv = atividades.findById(sessao.getAtividadeId()).filter(Atividade::isAtiva)
                .orElseThrow(() -> Util.naoEncontrado("Atividade"));

        LocalDate dia = sessao.getInicio().atZoneSameInstant(Util.FUSO).toLocalDate();
        if (dia.isBefore(r.getCheckIn()) || dia.isAfter(r.getCheckOut())) {
            throw Util.validacao("A sessão do dia " + dia + " está fora da sua estadia (" + r.getCheckIn() + " a " + r.getCheckOut() + ").");
        }
        if (!sessao.getInicio().isAfter(Util.agora())) throw Util.validacao("Essa sessão já começou ou já passou.");

        // Idade mínima/máxima: quantas pessoas da reserva são elegíveis?
        long elegiveis = hospedes.findByReservaId(r.getId()).stream().filter(h -> {
            int idade = h.getTipo() == TipoHospede.ADULTO ? IDADE_NOMINAL_ADULTO : h.getIdade();
            return (atv.getIdadeMin() == null || idade >= atv.getIdadeMin())
                    && (atv.getIdadeMax() == null || idade <= atv.getIdadeMax());
        }).count();
        if (req.quantidade() > elegiveis) {
            throw Util.validacao("Sua tribo tem " + elegiveis + " pessoa(s) na faixa de idade desta atividade e você pediu " + req.quantidade() + ".");
        }

        // Atomico: só ocupa se ainda couber. Evita vender a mesma vaga duas vezes.
        if (sessoes.ocuparVagas(sessao.getId(), req.quantidade()) == 0) {
            int livres = sessoes.findById(sessao.getId()).map(x -> x.getVagasTotais() - x.getVagasOcupadas()).orElse(0);
            throw new ApiException(HttpStatus.CONFLICT, "VAGAS_ESGOTADAS", "Ugh! As vagas acabaram.",
                    "Restam " + livres + " vaga(s) neste horário e você pediu " + req.quantidade()
                            + ". Nada foi reservado nem cobrado. Escolha outro horário ou toque em \"Avisar\".",
                    Map.of("vagasLivres", livres));
        }
        AgendamentoAtividade a = new AgendamentoAtividade();
        a.setReservaId(r.getId());
        a.setSessaoId(sessao.getId());
        a.setQuantidade(req.quantidade());
        a.setValorCentavos(atv.getPrecoCentavos() * req.quantidade()); // vai para a conta da toca (pago no hotel)
        a.setStatus(StatusAgendamento.ATIVO);
        a.setCriadoEm(Util.agora());
        return toRes(agendamentos.save(a), atv, sessao);
    }

    @Transactional
    public void cancelar(String codigo, UUID agendamentoId, Solicitante s) {
        Reserva r = reservaService.carregarDonoParaEscrita(codigo, s);
        AgendamentoAtividade a = agendamentos.findById(agendamentoId)
                .filter(x -> x.getReservaId().equals(r.getId())).orElseThrow(() -> Util.naoEncontrado("Agendamento"));
        if (a.getStatus() != StatusAgendamento.ATIVO) throw Util.estadoInvalido("Esse agendamento já foi cancelado.");
        SessaoAtividade sessao = sessoes.findById(a.getSessaoId()).orElseThrow(() -> Util.naoEncontrado("Sessão"));
        if (Util.agora().isAfter(sessao.getInicio().minus(Duration.ofHours(CANCELAR_HORAS_ANTES)))) {
            throw Util.foraDoPrazo("O cancelamento vai até " + CANCELAR_HORAS_ANTES + " horas antes da atividade.");
        }
        sessoes.liberarVagas(sessao.getId(), a.getQuantidade()); // devolve a vaga
        a.setStatus(StatusAgendamento.CANCELADO);
        agendamentos.save(a);
    }

    /** Agenda da tribo no dia: café, atividades e mesa. */
    @Transactional(readOnly = true)
    public AgendaRes agenda(String codigo, LocalDate data, Solicitante s) {
        Reserva r = reservaService.carregarDono(codigo, s);
        LocalDate dia = data != null ? data : r.getCheckIn();
        if (dia.isBefore(r.getCheckIn()) || dia.isAfter(r.getCheckOut())) {
            throw Util.validacao("Esse dia está fora da sua estadia.");
        }
        List<AgendaItemRes> itens = new ArrayList<>();
        if (dia.isAfter(r.getCheckIn())) {
            itens.add(new AgendaItemRes("07:00", "CAFE", "Café jurássico", "Incluso na sua reserva"));
        }
        for (AgendamentoAtividade a : agendamentos.findByReservaIdAndStatus(r.getId(), StatusAgendamento.ATIVO)) {
            sessoes.findById(a.getSessaoId()).ifPresent(sessao -> {
                var local = sessao.getInicio().atZoneSameInstant(Util.FUSO);
                if (local.toLocalDate().equals(dia)) {
                    String nome = atividades.findById(sessao.getAtividadeId()).map(Atividade::getNome).orElse("Atividade");
                    itens.add(new AgendaItemRes(local.toLocalTime().toString().substring(0, 5), "ATIVIDADE", nome,
                            a.getQuantidade() + " pessoa(s)"));
                }
            });
        }
        mesas.findByReservaId(r.getId()).stream()
                .filter(m -> m.getStatus() == StatusMesa.CONFIRMADA && m.getData().equals(dia))
                .forEach(m -> itens.add(new AgendaItemRes(m.getHorario().toString().substring(0, 5), "MESA",
                        "O Banquete", "Mesa para " + m.getPessoas() + " (" + m.getCodigo() + ")")));
        itens.sort(Comparator.comparing(AgendaItemRes::hora));
        return new AgendaRes(dia, itens);
    }

    /** Opcional no projeto: só grava o pedido, sem job de notificação. */
    @Transactional
    public void registrarAlerta(AlertaReq req, UUID usuarioId) {
        AlertaVaga a = new AlertaVaga();
        a.setUsuarioId(usuarioId);
        a.setEmail(req.email().trim().toLowerCase());
        a.setTipo(req.tipo().toUpperCase());
        a.setCavernaId(req.cavernaId());
        a.setSessaoId(req.sessaoId());
        a.setCheckIn(req.checkIn());
        a.setCheckOut(req.checkOut());
        a.setPessoas(req.pessoas());
        alertas.save(a);
    }

    // ---------- mapeamento ----------

    private AtividadeRes toRes(Atividade a) {
        return new AtividadeRes(a.getId(), a.getNome(), a.getCategoria(), a.getDescricao(), a.getDuracaoMin(),
                a.getIdadeMin(), a.getIdadeMax(), a.getPrecoCentavos(), a.getCobranca().name(), a.getDestaque());
    }

    private SessaoRes toRes(SessaoAtividade s) {
        int livres = s.getVagasTotais() - s.getVagasOcupadas();
        return new SessaoRes(s.getId(), s.getInicio(), s.getVagasTotais(), livres, livres <= 0);
    }

    private AgendamentoRes toRes(AgendamentoAtividade a, Atividade atv, SessaoAtividade s) {
        return new AgendamentoRes(a.getId(), s.getId(), atv.getNome(), s.getInicio(), a.getQuantidade(),
                a.getValorCentavos(), a.getStatus());
    }
}
