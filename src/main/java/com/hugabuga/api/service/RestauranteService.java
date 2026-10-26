package com.hugabuga.api.service;

import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.dto.RestauranteDtos.*;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/** Turnos, cardápio, horários LIVRE/LOTADO e reserva de mesa. */
@Service
@RequiredArgsConstructor
public class RestauranteService {

    private static final int CANCELAR_HORAS_ANTES = 2;

    private final ItemCardapioRepository cardapio;
    private final HorarioMesaRepository horariosMesa;
    private final ReservaMesaRepository mesas;
    private final ReservaService reservaService;

    // Turnos fixos lidos do application.yml (com padrão do guia)
    @Value("${hugabuga.restaurante.cafe-inicio:07:00}") private String cafeInicio;
    @Value("${hugabuga.restaurante.cafe-fim:10:30}") private String cafeFim;
    @Value("${hugabuga.restaurante.almoco-inicio:12:00}") private String almocoInicio;
    @Value("${hugabuga.restaurante.almoco-fim:15:00}") private String almocoFim;
    @Value("${hugabuga.restaurante.jantar-inicio:19:00}") private String jantarInicio;
    @Value("${hugabuga.restaurante.jantar-fim:23:00}") private String jantarFim;
    @Value("${hugabuga.regras.desconto-hospede-pct:15}") private int descontoHospedePct;

    public RestauranteRes visaoGeral() {
        List<TurnoRes> turnos = List.of(
                new TurnoRes("Café", LocalTime.parse(cafeInicio), LocalTime.parse(cafeFim)),
                new TurnoRes("Almoço", LocalTime.parse(almocoInicio), LocalTime.parse(almocoFim)),
                new TurnoRes("Jantar", LocalTime.parse(jantarInicio), LocalTime.parse(jantarFim)));
        LocalTime agora = ZonedDateTime.now(Util.FUSO).toLocalTime();
        TurnoRes atual = turnos.stream()
                .filter(t -> !agora.isBefore(t.abre()) && agora.isBefore(t.fecha())).findFirst().orElse(null);
        return new RestauranteRes(turnos, atual != null, atual == null ? null : atual.nome(), descontoHospedePct);
    }

    @Transactional(readOnly = true)
    public List<ItemCardapioRes> cardapio(CategoriaCardapio categoria) {
        return cardapio.findByAtivoTrueOrderByOrdem().stream()
                .filter(i -> categoria == null || i.getCategoria() == categoria)
                .map(i -> new ItemCardapioRes(i.getId(), i.getNome(), i.getDescricao(), i.getPrecoCentavos(),
                        i.getCategoria(), i.getTags())).toList();
    }

    /** LOTADO quando (já reservados + pessoas pedidas) passa da capacidade do horário. */
    @Transactional(readOnly = true)
    public List<HorarioRes> horarios(LocalDate data, int pessoas) {
        validarPedido(data, pessoas);
        return horariosMesa.findAllByOrderByHorario().stream().map(h -> {
            int restantes = h.getCapacidadePessoas() - mesas.somaPessoas(data, h.getHorario());
            return new HorarioRes(h.getHorario(), restantes >= pessoas ? "LIVRE" : "LOTADO", Math.max(0, restantes));
        }).toList();
    }

    @Transactional
    public MesaRes reservarMesa(MesaReq req, Solicitante s) {
        Reserva r = reservaService.carregarDono(req.codigoReserva(), s);
        reservaService.exigirStatus(r, StatusReserva.CONFIRMADA); // só hóspede com reserva confirmada
        validarPedido(req.data(), req.pessoas());
        if (req.data().isBefore(r.getCheckIn()) || req.data().isAfter(r.getCheckOut())) {
            throw Util.validacao("A data da mesa precisa estar dentro da sua estadia (" + r.getCheckIn() + " a " + r.getCheckOut() + ").");
        }
        if (req.cadeirinhas() > req.pessoas()) throw Util.validacao("Não pode haver mais cadeirinhas que pessoas.");
        if (req.data().equals(Util.hoje()) && !req.horario().isAfter(ZonedDateTime.now(Util.FUSO).toLocalTime())) {
            throw Util.validacao("Esse horário já passou.");
        }

        // Trava a linha do horário: duas reservas simultâneas não estouram a capacidade.
        HorarioMesa h = horariosMesa.travarPorHorario(req.horario()).orElseThrow(() -> Util.naoEncontrado("Horário"));
        int usados = mesas.somaPessoas(req.data(), h.getHorario());
        if (usados + req.pessoas() > h.getCapacidadePessoas()) {
            int livres = Math.max(0, h.getCapacidadePessoas() - usados);
            throw new ApiException(HttpStatus.CONFLICT, "VAGAS_ESGOTADAS", "Ugh! A caverna do Banquete lotou.",
                    "Esse horário tem " + livres + " lugar(es) livre(s) e você pediu " + req.pessoas()
                            + ". Nenhuma mesa foi reservada. Escolha outro horário.", Map.of("vagasRestantes", livres));
        }
        ReservaMesa m = new ReservaMesa();
        m.setCodigo(codigoUnico());
        m.setUsuarioId(r.getUsuarioId());
        m.setReservaId(r.getId());
        m.setNomeContato(req.nomeContato() != null && !req.nomeContato().isBlank() ? req.nomeContato().trim() : r.getResponsavelNome());
        m.setCelularContato(req.celularContato() != null && !req.celularContato().isBlank() ? req.celularContato() : r.getResponsavelCelular());
        m.setData(req.data());
        m.setHorario(h.getHorario());
        m.setPessoas(req.pessoas());
        m.setCadeirinhas(req.cadeirinhas());
        m.setPertoFogueira(req.pertoFogueira());
        m.setStatus(StatusMesa.CONFIRMADA);
        m.setCriadoEm(Util.agora());
        return toRes(mesas.save(m));
    }

    @Transactional(readOnly = true)
    public MesaRes detalhe(String codigo, Solicitante s) {
        return toRes(carregarMesaDoDono(codigo, s));
    }

    @Transactional
    public MesaRes cancelar(String codigo, Solicitante s) {
        ReservaMesa m = carregarMesaDoDono(codigo, s);
        if (m.getStatus() != StatusMesa.CONFIRMADA) throw Util.estadoInvalido("Essa mesa já foi cancelada ou encerrada.");
        ZonedDateTime inicio = m.getData().atTime(m.getHorario()).atZone(Util.FUSO);
        if (ZonedDateTime.now(Util.FUSO).isAfter(inicio.minus(Duration.ofHours(CANCELAR_HORAS_ANTES)))) {
            throw Util.foraDoPrazo("A mesa só pode ser cancelada até " + CANCELAR_HORAS_ANTES + " horas antes do horário.");
        }
        m.setStatus(StatusMesa.CANCELADA);
        return toRes(mesas.save(m));
    }

    // ---------- internos ----------

    private ReservaMesa carregarMesaDoDono(String codigo, Solicitante s) {
        ReservaMesa m = mesas.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> Util.naoEncontrado("Reserva de mesa"));
        reservaService.verificarDono(reservaService.carregarPorId(m.getReservaId()), s);
        return m;
    }

    private void validarPedido(LocalDate data, int pessoas) {
        if (pessoas < 1 || pessoas > 12) throw Util.validacao("A mesa comporta de 1 a 12 pessoas.");
        if (data == null || data.isBefore(Util.hoje())) throw Util.validacao("Escolha uma data a partir de hoje.");
    }

    private String codigoUnico() {
        for (int i = 0; i < 20; i++) {
            String c = Util.codigo("BQ");
            if (mesas.findByCodigo(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código de mesa único.");
    }

    private MesaRes toRes(ReservaMesa m) {
        return new MesaRes(m.getCodigo(), m.getData(), m.getHorario(), m.getPessoas(), m.getCadeirinhas(),
                m.isPertoFogueira(), m.getStatus(), m.getNomeContato(), descontoHospedePct);
    }
}
