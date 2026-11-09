package com.hugabuga.api.service;

import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.dto.RestauranteDtos.*;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Turnos, cardápio, horários LIVRE/LOTADO e reserva de mesa. A mesa pode ser:
 *  - de hóspede (com o código de uma estadia CONFIRMADA): ganha o desconto de hóspede;
 *  - de visitante de fora (sem estadia): só precisa de nome, celular e e-mail.
 * O combo quarto + Banquete é escolhido no checkout (ReservaService) e gera as próprias mesas.
 */
@Service
@RequiredArgsConstructor
public class RestauranteService {

    private static final int CANCELAR_HORAS_ANTES = 2;
    private static final int PESSOAS_MAX = 12;

    private final ItemCardapioRepository cardapio;
    private final ReservaMesaRepository mesas;
    private final ReservaRepository reservas;
    private final UsuarioRepository usuarios;
    private final ReservaService reservaService;
    private final OcupacaoBanqueteService ocupacao;

    // Turnos fixos lidos do application.yml (com padrão do guia)
    @Value("${hugabuga.restaurante.cafe-inicio:07:00}") private String cafeInicio;
    @Value("${hugabuga.restaurante.cafe-fim:10:30}") private String cafeFim;
    @Value("${hugabuga.restaurante.almoco-inicio:12:00}") private String almocoInicio;
    @Value("${hugabuga.restaurante.almoco-fim:15:00}") private String almocoFim;
    @Value("${hugabuga.restaurante.jantar-inicio:19:00}") private String jantarInicio;
    @Value("${hugabuga.restaurante.jantar-fim:23:00}") private String jantarFim;
    @Value("${hugabuga.restaurante.antecedencia-max-dias:60}") private int antecedenciaMaxDias;
    @Value("${hugabuga.regras.desconto-hospede-pct:15}") private int descontoHospedePct;

    public RestauranteRes visaoGeral() {
        List<TurnoRes> turnos = List.of(
                new TurnoRes("Café", LocalTime.parse(cafeInicio), LocalTime.parse(cafeFim)),
                new TurnoRes("Almoço", LocalTime.parse(almocoInicio), LocalTime.parse(almocoFim)),
                new TurnoRes("Jantar", LocalTime.parse(jantarInicio), LocalTime.parse(jantarFim)));
        LocalTime agora = ZonedDateTime.now(Util.FUSO).toLocalTime();
        TurnoRes atual = turnos.stream()
                .filter(t -> !agora.isBefore(t.abre()) && agora.isBefore(t.fecha())).findFirst().orElse(null);
        return new RestauranteRes(turnos, atual != null, atual == null ? null : atual.nome(), descontoHospedePct,
                ocupacao.capacidade());
    }

    @Transactional(readOnly = true)
    public List<ItemCardapioRes> cardapio(CategoriaCardapio categoria) {
        return cardapio.findByAtivoTrueOrderByOrdem().stream()
                .filter(i -> categoria == null || i.getCategoria() == categoria)
                .map(i -> new ItemCardapioRes(i.getId(), i.getNome(), i.getDescricao(), i.getPrecoCentavos(),
                        i.getCategoria(), i.getTags())).toList();
    }

    /** LOTADO quando não cabem as pessoas pedidas em algum momento da refeição (salão de 50 lugares). */
    @Transactional(readOnly = true)
    public List<HorarioRes> horarios(LocalDate data, int pessoas) {
        validarPedido(data, pessoas);
        return ocupacao.horarios(data, pessoas);
    }

    @Transactional
    public MesaRes reservarMesa(MesaReq req, Solicitante s) {
        validarPedido(req.data(), req.pessoas());
        if (req.cadeirinhas() > req.pessoas()) throw Util.validacao("Não pode haver mais cadeirinhas que pessoas.");
        if (req.data().equals(Util.hoje()) && !req.horario().isAfter(ZonedDateTime.now(Util.FUSO).toLocalTime())) {
            throw Util.validacao("Esse horário já passou.");
        }

        ReservaMesa m = new ReservaMesa();
        boolean hospede = req.codigoReserva() != null && !req.codigoReserva().isBlank();
        if (hospede) {
            Reserva r = reservaService.carregarDono(req.codigoReserva(), s);
            reservaService.exigirStatus(r, StatusReserva.CONFIRMADA); // só hóspede com reserva confirmada
            if (req.data().isBefore(r.getCheckIn()) || req.data().isAfter(r.getCheckOut())) {
                throw Util.validacao("A data da mesa precisa estar dentro da sua estadia (" + r.getCheckIn() + " a " + r.getCheckOut() + ").");
            }
            m.setOrigem(OrigemMesa.HOSPEDE);
            m.setReservaId(r.getId());
            m.setUsuarioId(r.getUsuarioId());
            m.setNomeContato(texto(req.nomeContato(), r.getResponsavelNome()));
            m.setEmailContato(r.getResponsavelEmail());
            m.setCelularContato(texto(req.celularContato(), r.getResponsavelCelular()));
        } else {
            // Visitante de fora: só precisa de contato. Se estiver logado, a mesa aparece em "Minhas reservas".
            if (req.data().isAfter(Util.hoje().plusDays(antecedenciaMaxDias))) {
                throw Util.validacao("Mesas para quem não está hospedado abrem com até " + antecedenciaMaxDias + " dias de antecedência.");
            }
            Usuario u = s.usuarioId() == null ? null : usuarios.findById(s.usuarioId()).orElse(null);
            String nome = texto(req.nomeContato(), u == null ? null : u.getNome() + " " + u.getSobrenome());
            String email = texto(req.emailContato(), u == null ? null : u.getEmail());
            String celular = texto(req.celularContato(), u == null ? null : u.getCelular());
            if (nome == null || email == null || celular == null) {
                throw Util.validacao("Para reservar sem estadia, informe nome, e-mail e celular de contato.");
            }
            m.setOrigem(OrigemMesa.EXTERNO);
            m.setUsuarioId(u == null ? null : u.getId());
            m.setNomeContato(nome);
            m.setEmailContato(email.toLowerCase());
            m.setCelularContato(celular);
        }

        // Trava o salão: duas reservas simultâneas não estouram os 50 lugares.
        ocupacao.garantirLugares(req.data(), req.horario(), req.pessoas());

        m.setCodigo(codigoUnico());
        m.setData(req.data());
        m.setHorario(req.horario());
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
        if (m.getOrigem() == OrigemMesa.COMBO) {
            throw Util.estadoInvalido("Esta mesa faz parte do combo da sua estadia. Para tirar o combo, altere ou cancele a reserva.");
        }
        if (m.getStatus() != StatusMesa.CONFIRMADA) throw Util.estadoInvalido("Essa mesa já foi cancelada ou encerrada.");
        ZonedDateTime inicio = m.getData().atTime(m.getHorario()).atZone(Util.FUSO);
        if (ZonedDateTime.now(Util.FUSO).isAfter(inicio.minus(Duration.ofHours(CANCELAR_HORAS_ANTES)))) {
            throw Util.foraDoPrazo("A mesa só pode ser cancelada até " + CANCELAR_HORAS_ANTES + " horas antes do horário.");
        }
        m.setStatus(StatusMesa.CANCELADA);
        return toRes(mesas.save(m));
    }

    // ---------- internos ----------

    /**
     * Mesa de hóspede/combo: dono da estadia. Mesa de visitante de fora: a conta que reservou ou o e-mail de
     * contato no header X-Responsavel-Email. ADMIN vê qualquer uma.
     */
    private ReservaMesa carregarMesaDoDono(String codigo, Solicitante s) {
        ReservaMesa m = mesas.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> Util.naoEncontrado("Reserva de mesa"));
        if (m.getReservaId() != null) {
            reservaService.verificarDono(reservaService.carregarPorId(m.getReservaId()), s);
        } else if (!s.admin()) {
            boolean donoConta = m.getUsuarioId() != null && m.getUsuarioId().equals(s.usuarioId());
            boolean donoEmail = s.email() != null && s.email().trim().equalsIgnoreCase(m.getEmailContato());
            if (!donoConta && !donoEmail) throw Util.semPermissao();
        }
        return m;
    }

    private void validarPedido(LocalDate data, int pessoas) {
        if (pessoas < 1 || pessoas > PESSOAS_MAX) throw Util.validacao("A mesa comporta de 1 a " + PESSOAS_MAX + " pessoas.");
        if (data == null || data.isBefore(Util.hoje())) throw Util.validacao("Escolha uma data a partir de hoje.");
    }

    private static String texto(String valor, String padrao) {
        return valor != null && !valor.isBlank() ? valor.trim() : padrao;
    }

    private String codigoUnico() {
        for (int i = 0; i < 20; i++) {
            String c = Util.codigo("BQ");
            if (mesas.findByCodigo(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código de mesa único.");
    }

    private MesaRes toRes(ReservaMesa m) {
        String codigoReserva = m.getReservaId() == null ? null
                : reservas.findById(m.getReservaId()).map(Reserva::getCodigo).orElse(null);
        int desconto = m.getOrigem() == OrigemMesa.EXTERNO ? 0 : descontoHospedePct;
        return new MesaRes(m.getCodigo(), m.getOrigem(), codigoReserva, m.getData(), m.getHorario(), m.getPessoas(),
                m.getCadeirinhas(), m.isPertoFogueira(), m.getStatus(), m.getNomeContato(), desconto);
    }
}
