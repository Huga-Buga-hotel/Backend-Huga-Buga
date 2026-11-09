package com.hugabuga.api.service;

import com.hugabuga.api.dto.CavernaDtos.CotacaoRes;
import com.hugabuga.api.dto.ReservaDtos.*;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import org.openpdf.text.Document;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Todo o checkout: criar, hóspedes, extras, combo do Banquete, cupom, voucher, cancelar, alterar datas e check-in. */
@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final String LATE_CHECKOUT_ID = "LATE_CHECKOUT";
    private static final int IDADE_GRATIS_MAX = 5;     // combo: até 5 anos não paga
    private static final int IDADE_CRIANCA_MAX = 12;   // combo: 6 a 12 paga preço de criança; acima, de adulto
    private static final int PRAZO_DIAS = 2;          // cancelar / alterar
    private static final int CHECKIN_DIAS = 3;        // check-in online libera 3 dias antes

    /** Máquina de estados (seção 7 do guia). Qualquer transição fora dela é rejeitada. */
    private static final Map<StatusReserva, Set<StatusReserva>> TRANSICOES = new EnumMap<>(StatusReserva.class);
    static {
        TRANSICOES.put(StatusReserva.PENDENTE, EnumSet.of(StatusReserva.AGUARDANDO_PAGAMENTO,
                StatusReserva.CONFIRMADA, StatusReserva.EXPIRADA, StatusReserva.CANCELADA));
        TRANSICOES.put(StatusReserva.AGUARDANDO_PAGAMENTO, EnumSet.of(StatusReserva.CONFIRMADA, StatusReserva.EXPIRADA));
        TRANSICOES.put(StatusReserva.CONFIRMADA, EnumSet.of(StatusReserva.CANCELADA, StatusReserva.CONCLUIDA));
        TRANSICOES.put(StatusReserva.CANCELADA, EnumSet.noneOf(StatusReserva.class));
        TRANSICOES.put(StatusReserva.EXPIRADA, EnumSet.noneOf(StatusReserva.class));
        TRANSICOES.put(StatusReserva.CONCLUIDA, EnumSet.noneOf(StatusReserva.class));
    }

    private final ReservaRepository reservas;
    private final UnidadeCavernaRepository unidades;
    private final CavernaRepository cavernas;
    private final CavernaService cavernaService;
    private final HospedeReservaRepository hospedes;
    private final ExtraRepository extras;
    private final ReservaExtraRepository reservaExtras;
    private final CupomRepository cupons;
    private final UsuarioRepository usuarios;
    private final PagamentoRepository pagamentos;
    private final ComboBanqueteRepository combos;
    private final OcupacaoBanqueteService ocupacaoBanquete;
    private final AgendamentoAtividadeRepository agendamentos;
    private final SessaoAtividadeRepository sessoes;
    private final ReservaMesaRepository mesas;

    @Value("${hugabuga.regras.hold-minutos:15}") private int holdMinutos;
    @Value("${hugabuga.regras.prorrogacao-minutos:10}") private int prorrogacaoMinutos;
    @Value("${hugabuga.regras.pix-percentual:5}") private int pixPercentual;

    // =====================================================================
    // Cálculo de valores: ÚNICO lugar com a conta do total
    // =====================================================================

    /** Ordem: diárias + extras + combo − cupom. O desconto do Pix é aplicado por último, só no pagamento. */
    private void recalcular(Reserva r) {
        Cupom cupom = r.getCupomId() == null ? null : cupons.findById(r.getCupomId()).orElse(null);
        int bruto = r.getValorDiarias() + r.getValorExtras() + r.getValorCombo();
        int descontoCupom = descontoCupom(cupom, bruto);
        r.setDesconto(descontoCupom);
        r.setTotal(bruto - descontoCupom);
        r.setAtualizadoEm(Util.agora());
    }

    private int descontoCupom(Cupom c, int bruto) {
        if (c == null) return 0;
        return c.getTipo() == TipoCupom.PERCENTUAL
                ? Util.porcentagem(bruto, c.getValor())
                : Math.min(bruto, c.getValor());
    }

    /** Pix: total − 5% (161100 → 153045). A tela arredonda só na exibição. */
    public int valorComPix(int totalCentavos) {
        return totalCentavos - Util.porcentagem(totalCentavos, pixPercentual);
    }

    /** Parcela arredondada (161100 / 3 = 53700). */
    public int valorParcela(int totalCentavos, int parcelas) {
        return (totalCentavos + parcelas / 2) / parcelas;
    }

    // =====================================================================
    // Passo 1: criar, hóspedes, prorrogar
    // =====================================================================

    @Transactional
    public ReservaRes criar(CriarReservaReq req, Solicitante s) {
        List<Integer> idades = req.idadesCriancas() == null ? List.of() : req.idadesCriancas();
        CotacaoRes cot = cavernaService.cotar(req.cavernaId(), req.checkIn(), req.checkOut(), req.adultos(), idades);
        Caverna caverna = cavernas.findById(req.cavernaId()).orElseThrow(() -> Util.naoEncontrado("Caverna"));

        // Nunca confiamos no preço do front: recalculamos e só comparamos.
        if (req.precoEsperadoCentavos() != null && req.precoEsperadoCentavos() != cot.valorDiariasCentavos()) {
            throw new ApiException(HttpStatus.CONFLICT, "PRECO_ALTERADO", "Ugh! O preço mudou.",
                    "O valor da estadia mudou desde a sua busca: era " + Util.reais(req.precoEsperadoCentavos())
                            + " e agora é " + Util.reais(cot.valorDiariasCentavos())
                            + ". Nenhuma reserva foi criada. Confira o novo valor e continue se quiser.",
                    Map.of("precoAntigoCentavos", req.precoEsperadoCentavos(),
                            "precoNovoCentavos", cot.valorDiariasCentavos()));
        }

        String email = req.email();
        if ((email == null || email.isBlank()) && s.usuarioId() != null) {
            email = usuarios.findById(s.usuarioId()).map(Usuario::getEmail).orElse(null);
        }
        if (email == null || email.isBlank()) throw Util.validacao("Informe o e-mail do responsável pela reserva.");

        // Bloqueio de linha (SKIP LOCKED): duas tribos nunca pegam a mesma toca.
        UnidadeCaverna unidade = unidades.primeiraLivreComBloqueio(req.cavernaId(), req.checkIn(), req.checkOut())
                .orElseThrow(() -> semDisponibilidade(caverna, req.checkIn(), req.checkOut()));

        Reserva r = new Reserva();
        r.setCodigo(codigoUnico());
        r.setUsuarioId(s.usuarioId());
        r.setCavernaId(req.cavernaId());
        r.setUnidadeId(unidade.getId());
        r.setCheckIn(req.checkIn());
        r.setCheckOut(req.checkOut());
        r.setAdultos(req.adultos());
        r.setCriancas(idades.size());
        r.setStatus(StatusReserva.PENDENTE);
        r.setExpiraEm(Util.agora().plusMinutes(holdMinutos));
        r.setProrrogada(false);
        r.setResponsavelEmail(email.trim().toLowerCase());
        r.setValorDiarias(cot.valorDiariasCentavos()); // preço congelado agora
        r.setValorExtras(0);
        r.setValorCombo(0);
        r.setLateCheckout(false);
        r.setCriadoEm(Util.agora());
        recalcular(r);
        try {
            reservas.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) { // restrição de exclusão do banco: última barreira
            throw semDisponibilidade(caverna, req.checkIn(), req.checkOut());
        }
        return toRes(r);
    }

    @Transactional
    public ReservaRes salvarHospedes(String codigo, HospedesReq req, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);

        int adultos = (int) req.hospedes().stream().filter(h -> h.tipo() == TipoHospede.ADULTO).count();
        if (adultos < 1) throw Util.validacao("É preciso ao menos 1 adulto na reserva.");
        List<Integer> idades = req.hospedes().stream().filter(h -> h.tipo() == TipoHospede.CRIANCA).map(h -> {
            if (h.idade() == null || h.idade() < 0 || h.idade() > 17) {
                throw Util.validacao("Informe a idade de " + h.nome() + " (0 a 17 anos).");
            }
            return h.idade();
        }).toList();

        // Recalcula: criança acima de 12 anos vira adulto e o total sobe.
        CotacaoRes cot = cavernaService.cotar(r.getCavernaId(), r.getCheckIn(), r.getCheckOut(), adultos, idades);

        hospedes.deleteByReservaId(r.getId());
        hospedes.flush();
        for (HospedeDto h : req.hospedes()) {
            HospedeReserva hr = new HospedeReserva();
            hr.setReservaId(r.getId());
            hr.setNome(h.nome().trim());
            hr.setTipo(h.tipo());
            hr.setIdade(h.tipo() == TipoHospede.CRIANCA ? h.idade() : null);
            hospedes.save(hr);
        }
        r.setAdultos(adultos);
        r.setCriancas(idades.size());
        r.setValorDiarias(cot.valorDiariasCentavos());
        r.setResponsavelNome(req.responsavelNome().trim());
        r.setResponsavelEmail(req.responsavelEmail().trim().toLowerCase());
        r.setResponsavelCelular(req.celular());
        r.setResponsavelPais(req.pais().toUpperCase());
        r.setResponsavelTipoDocumento(req.tipoDocumento().toUpperCase());
        r.setResponsavelDocumento(req.documento());
        r.setPedidoEspecial(req.pedidoEspecial());
        if (r.getComboId() != null) { // a tribo mudou: o combo é recalculado e as mesas ajustadas ao novo tamanho
            hospedes.flush();
            reservarMesasCombo(r, StatusMesa.PENDENTE);
            r.setValorCombo(valorCombo(r, combo(r.getComboId())));
        }
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    @Transactional
    public ReservaRes prorrogar(String codigo, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);
        if (r.isProrrogada()) {
            throw Util.estadoInvalido("O tempo extra já foi usado nesta reserva (só dá uma vez).");
        }
        r.setExpiraEm(r.getExpiraEm().plusMinutes(prorrogacaoMinutos));
        r.setProrrogada(true);
        r.setAtualizadoEm(Util.agora());
        reservas.save(r);
        return toRes(r);
    }

    // =====================================================================
    // Passo 2: extras
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ExtraRes> listarExtras() {
        return extras.findByAtivoTrue().stream()
                .map(e -> new ExtraRes(e.getId(), e.getNome(), e.getDescricao(), e.getIcone(),
                        e.getPrecoCentavos(), e.getCobranca(), e.isIncluso())).toList();
    }

    /** Recebe a lista COMPLETA; lista vazia = "Pular extras". */
    @Transactional
    public ReservaRes salvarExtras(String codigo, ExtrasReq req, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);

        reservaExtras.deleteByReservaId(r.getId());
        reservaExtras.flush(); // chave composta: apaga antes de reinserir o mesmo extra

        int total = 0;
        Set<String> vistos = new HashSet<>();
        for (ExtraItemReq item : req.extras() == null ? List.<ExtraItemReq>of() : req.extras()) {
            if (!vistos.add(item.extraId())) continue;
            Extra e = extras.findById(item.extraId()).filter(Extra::isAtivo)
                    .orElseThrow(() -> Util.naoEncontrado("O extra " + item.extraId()));
            if (e.isIncluso()) continue; // café jurássico já vem em toda reserva
            int qtd = switch (e.getCobranca()) {
                case POR_CRIANCA -> r.getCriancas();
                case POR_PESSOA -> r.getAdultos() + r.getCriancas();
                case POR_ESTADIA -> Math.max(1, Math.min(item.quantidade() == null ? 1 : item.quantidade(), 10));
            };
            if (qtd <= 0) throw Util.validacao("O extra " + e.getNome() + " é cobrado por criança e esta reserva não tem crianças.");
            ReservaExtra re = new ReservaExtra();
            re.setReservaId(r.getId());
            re.setExtraId(e.getId());
            re.setQuantidade(qtd);
            re.setPrecoUnitarioCentavos(e.getPrecoCentavos());     // preço congelado
            re.setSubtotalCentavos(e.getPrecoCentavos() * qtd);
            reservaExtras.save(re);
            total += re.getSubtotalCentavos();
        }
        r.setValorExtras(total);
        r.setLateCheckout(vistos.contains(LATE_CHECKOUT_ID));
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    // =====================================================================
    // Passo 3: cupom
    // =====================================================================

    @Transactional
    public ReservaRes aplicarCupom(String codigo, CupomReq req, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);
        Cupom c = cupons.findByCodigoIgnoreCase(req.codigo().trim()).orElseThrow(this::cupomInvalido);
        validarCupom(c);
        r.setCupomId(c.getId()); // um cupom por vez; "usos" só sobe na confirmação
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    @Transactional
    public ReservaRes removerCupom(String codigo, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);
        r.setCupomId(null);
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    // =====================================================================
    // Passo 2 (continuação): combo quarto + Banquete
    // =====================================================================

    @Transactional(readOnly = true)
    public List<ComboRes> listarCombos() {
        return combos.findByAtivoTrueOrderByPrecoAdultoCentavos().stream()
                .map(c -> new ComboRes(c.getId(), c.getNome(), c.getDescricao(), c.getPrecoAdultoCentavos(),
                        c.getPrecoCriancaCentavos())).toList();
    }

    /**
     * Escolhe o combo: jantar no Banquete todas as noites, no mesmo horário. As mesas já são reservadas agora
     * (PENDENTE, segurando lugar no salão como a toca é segurada) e viram CONFIRMADA no pagamento.
     */
    @Transactional
    public ReservaRes definirCombo(String codigo, ComboReq req, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);
        if (r.getResponsavelNome() == null) {
            throw Util.validacao("Preencha quem vem pra caverna (passo 1) antes de escolher o combo.");
        }
        ComboBanquete c = combo(req.comboId());
        r.setComboId(c.getId());
        r.setComboHorario(req.horario());
        reservarMesasCombo(r, StatusMesa.PENDENTE);
        r.setValorCombo(valorCombo(r, c));
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    @Transactional
    public ReservaRes removerCombo(String codigo, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE);
        cancelarMesasCombo(r);
        r.setComboId(null);
        r.setComboHorario(null);
        r.setValorCombo(0);
        recalcular(r);
        reservas.save(r);
        return toRes(r);
    }

    /** Libera os lugares do combo de uma reserva que expirou (chamado pelos jobs). */
    @Transactional
    public void liberarMesasCombo(Reserva r) { cancelarMesasCombo(r); }

    // =====================================================================
    // Confirmação (chamada pelo PagamentoService quando o pagamento é aprovado / "na chegada")
    // =====================================================================

    /** Valida TUDO antes de mexer em qualquer coisa: se falhar, nada fica pela metade. */
    @Transactional
    public void confirmar(Reserva r) {
        if (r.getStatus() == StatusReserva.CONFIRMADA) return;
        if (!TRANSICOES.get(r.getStatus()).contains(StatusReserva.CONFIRMADA)) {
            throw Util.estadoInvalido("A reserva está " + r.getStatus() + " e não pode ser confirmada.");
        }
        Cupom cupom = null;
        if (r.getCupomId() != null) {
            cupom = cupons.findById(r.getCupomId()).orElseThrow(this::cupomInvalido);
            validarCupom(cupom);
        }
        // --- a partir daqui só gravações ---
        r.setStatus(StatusReserva.CONFIRMADA);
        r.setAtualizadoEm(Util.agora());
        reservas.save(r);
        if (cupom != null) { cupom.setUsos(cupom.getUsos() + 1); cupons.save(cupom); }
        // as mesas do combo, que seguravam lugar no salão, passam a valer
        mesas.findByReservaIdAndOrigem(r.getId(), OrigemMesa.COMBO).stream()
                .filter(m -> m.getStatus() == StatusMesa.PENDENTE)
                .forEach(m -> { m.setStatus(StatusMesa.CONFIRMADA); mesas.save(m); });
    }

    // =====================================================================
    // Consulta: voucher, PDF, busca sem conta
    // =====================================================================

    @Transactional(readOnly = true)
    public ReservaRes voucher(String codigo, Solicitante s) {
        return toRes(carregarDono(codigo, s));
    }

    @Transactional(readOnly = true)
    public ReservaRes buscarSemConta(String codigo, String email) {
        // Exige código E e-mail. Qualquer divergência vira o mesmo 404 (não revela se o código existe).
        // TODO: limitar tentativas por IP (ex.: filtro com bucket4j), pois HB-XXXX é curto.
        Reserva r = reservas.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .filter(x -> email != null && x.getResponsavelEmail() != null
                        && x.getResponsavelEmail().equalsIgnoreCase(email.trim()))
                .orElseThrow(() -> Util.naoEncontrado("Reserva"));
        return toRes(r);
    }

    @Transactional(readOnly = true)
    public byte[] voucherPdf(String codigo, Solicitante s) {
        ReservaRes v = voucher(codigo, s);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Huga Buga Hotel - Tábua de reserva", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Código: " + v.codigo() + "   (" + v.status() + ")"));
            doc.add(new Paragraph("Caverna: " + v.cavernaNome() + (v.unidadeCodigo() == null ? "" : " - " + v.unidadeCodigo())));
            doc.add(new Paragraph("Entrada: " + v.checkIn() + " (a partir das 14h)"));
            doc.add(new Paragraph("Saída: " + v.checkOut() + (v.lateCheckout() ? " (até 15h, late check-out)" : " (até 12h)")));
            doc.add(new Paragraph("Noites: " + v.noites() + "   Adultos: " + v.adultos() + "   Crianças: " + v.criancas()));
            doc.add(new Paragraph("Responsável: " + v.responsavelNome()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Diárias: " + Util.reais(v.valorDiarias())));
            doc.add(new Paragraph("Extras: " + Util.reais(v.valorExtras())));
            if (v.combo() != null) {
                doc.add(new Paragraph("Combo " + v.combo().nome() + " (jantar às " + v.combo().horario() + "): "
                        + Util.reais(v.valorCombo())));
            }
            doc.add(new Paragraph("Desconto (cupom): " + Util.reais(v.desconto())));
            doc.add(new Paragraph("Total: " + Util.reais(v.total()) + (v.pago() ? " (pago)" : " (a pagar)")));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INDISPONIVEL", "Ugh! Não deu pra gerar a tábua.",
                    "O PDF não pôde ser gerado agora. Sua reserva continua válida. Tente de novo em instantes.");
        }
    }

    /** "Adicionar à agenda": arquivo iCalendar com a estadia (entrada 14h, saída 12h ou 15h com late check-out). */
    @Transactional(readOnly = true)
    public String agendaIcs(String codigo, Solicitante s) {
        ReservaRes v = voucher(codigo, s);
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String inicio = v.checkIn().atTime(14, 0).format(f);
        String fim = v.checkOut().atTime(v.lateCheckout() ? 15 : 12, 0).format(f);
        String agora = java.time.LocalDateTime.now(Util.FUSO).format(f);
        return String.join("\r\n",
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Huga Buga Hotel//Reservas//PT", "CALSCALE:GREGORIAN",
                "BEGIN:VEVENT",
                "UID:" + v.codigo() + "@hugabuga",
                "DTSTAMP;TZID=America/Sao_Paulo:" + agora,
                "DTSTART;TZID=America/Sao_Paulo:" + inicio,
                "DTEND;TZID=America/Sao_Paulo:" + fim,
                "SUMMARY:Huga Buga Hotel - " + v.cavernaNome(),
                "DESCRIPTION:Reserva " + v.codigo() + ". Entrada a partir das 14h.",
                "LOCATION:Huga Buga Hotel",
                "END:VEVENT", "END:VCALENDAR", "");
    }

    // =====================================================================
    // Gestão: alterar datas, cancelar, check-in
    // =====================================================================

    @Transactional
    public AlterarDatasRes alterarDatas(String codigo, AlterarDatasReq req, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.CONFIRMADA);
        if (ChronoUnit.DAYS.between(Util.hoje(), r.getCheckIn()) < PRAZO_DIAS) {
            throw Util.foraDoPrazo("As datas só podem ser alteradas até " + PRAZO_DIAS + " dias antes do check-in.");
        }
        List<Integer> idades = hospedes.findByReservaId(r.getId()).stream()
                .filter(h -> h.getTipo() == TipoHospede.CRIANCA).map(HospedeReserva::getIdade).toList();
        CotacaoRes cot = cavernaService.cotar(r.getCavernaId(), req.checkIn(), req.checkOut(), r.getAdultos(), idades);

        if (reservas.existeConflito(r.getUnidadeId(), req.checkIn(), req.checkOut(), r.getId())) {
            Caverna c = cavernas.findById(r.getCavernaId()).orElseThrow(() -> Util.naoEncontrado("Caverna"));
            throw semDisponibilidade(c, req.checkIn(), req.checkOut());
        }
        int totalAntigo = r.getTotal();
        List<Pagamento> pags = pagamentos.findByReservaId(r.getId());
        r.setCheckIn(req.checkIn());
        r.setCheckOut(req.checkOut());
        r.setValorDiarias(cot.valorDiariasCentavos());
        if (r.getComboId() != null) { // jantares do combo acompanham as novas noites
            reservarMesasCombo(r, StatusMesa.CONFIRMADA);
            r.setValorCombo(valorCombo(r, combo(r.getComboId())));
        }
        recalcular(r);
        int diferenca = r.getTotal() - totalAntigo;

        if (diferenca > 0) { // a diferença vira um novo pagamento
            Pagamento p = new Pagamento();
            p.setReservaId(r.getId());
            p.setMetodo(pags.stream().filter(x -> x.getStatus() == StatusPagamento.APROVADO)
                    .map(Pagamento::getMetodo).findFirst().orElse(MetodoPagamento.NA_CHEGADA));
            p.setStatus(StatusPagamento.PENDENTE);
            p.setValorCentavos(diferenca);
            p.setCriadoEm(Util.agora());
            pagamentos.save(p);
        }
        try {
            reservas.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            Caverna c = cavernas.findById(r.getCavernaId()).orElseThrow(() -> Util.naoEncontrado("Caverna"));
            throw semDisponibilidade(c, req.checkIn(), req.checkOut());
        }
        return new AlterarDatasRes(toRes(r), diferenca);
    }

    @Transactional
    public CancelarRes cancelar(String codigo, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.PENDENTE, StatusReserva.CONFIRMADA);

        int reembolso = 0, multa = 0;
        if (r.getStatus() == StatusReserva.CONFIRMADA) {
            List<Pagamento> pags = pagamentos.findByReservaId(r.getId());
            boolean temAprovado = pags.stream().anyMatch(p -> p.getStatus() == StatusPagamento.APROVADO);
            boolean eraNaChegada = !temAprovado && pags.stream().anyMatch(p -> p.getMetodo() == MetodoPagamento.NA_CHEGADA);
            // a cobrança "na chegada" original deixa de valer
            pags.stream().filter(p -> p.getMetodo() == MetodoPagamento.NA_CHEGADA && p.getStatus() == StatusPagamento.PENDENTE)
                    .forEach(p -> { p.setStatus(StatusPagamento.EXPIRADO); pagamentos.save(p); });

            if (ChronoUnit.DAYS.between(Util.hoje(), r.getCheckIn()) >= PRAZO_DIAS) {
                reembolso = pags.stream().filter(p -> p.getStatus() == StatusPagamento.APROVADO)
                        .mapToInt(Pagamento::getValorCentavos).sum();
            } else if (eraNaChegada) { // fora do prazo + pagar na chegada: multa de 1 diária
                multa = r.getValorDiarias() / Math.max(1, noites(r));
                Pagamento p = new Pagamento();
                p.setReservaId(r.getId());
                p.setMetodo(MetodoPagamento.NA_CHEGADA);
                p.setStatus(StatusPagamento.PENDENTE);
                p.setValorCentavos(multa);
                p.setCriadoEm(Util.agora());
                pagamentos.save(p);
            }
            // fora do prazo com pagamento aprovado: sem reembolso
        }
        cancelarDependencias(r);
        mudarStatus(r, StatusReserva.CANCELADA);
        r.setCanceladaEm(Util.agora());
        reservas.save(r);
        return new CancelarRes(toRes(r), reembolso, multa);
    }

    @Transactional
    public ReservaRes checkinOnline(String codigo, Solicitante s) {
        Reserva r = carregarDonoParaEscrita(codigo, s);
        exigirStatus(r, StatusReserva.CONFIRMADA);
        LocalDate hoje = Util.hoje();
        if (hoje.isBefore(r.getCheckIn().minusDays(CHECKIN_DIAS)) || hoje.isAfter(r.getCheckOut())) {
            throw Util.foraDoPrazo("O check-in online abre " + CHECKIN_DIAS + " dias antes da chegada ("
                    + r.getCheckIn().minusDays(CHECKIN_DIAS) + ").");
        }
        if (r.getCheckinOnlineEm() == null) {
            r.setCheckinOnlineEm(Util.agora());
            r.setAtualizadoEm(Util.agora());
            reservas.save(r);
        }
        return toRes(r);
    }

    // =====================================================================
    // Jobs (chamados pela TarefasAgendadas)
    // =====================================================================

    @Transactional
    public int expirarVencidas() {
        List<Reserva> vencidas = reservas.findByStatusAndExpiraEmBefore(StatusReserva.PENDENTE, Util.agora());
        vencidas.forEach(r -> {
            r.setStatus(StatusReserva.EXPIRADA); // libera a unidade
            r.setAtualizadoEm(Util.agora());
            cancelarMesasCombo(r);              // e os lugares do combo no Banquete
        });
        reservas.saveAll(vencidas);
        return vencidas.size();
    }

    @Transactional
    public int concluirEstadias() {
        List<Reserva> finalizadas = reservas.findByStatusAndCheckOutBefore(StatusReserva.CONFIRMADA, Util.hoje());
        for (Reserva r : finalizadas) {
            mudarStatus(r, StatusReserva.CONCLUIDA); // libera a avaliação da estadia
            reservas.save(r);
        }
        return finalizadas.size();
    }

    // =====================================================================
    // Apoio público (usado por Pagamento, Atividade e Restaurante)
    // =====================================================================

    @Transactional(readOnly = true)
    public Reserva carregarDono(String codigo, Solicitante s) {
        Reserva r = reservas.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> Util.naoEncontrado("Reserva"));
        verificarDono(r, s);
        return r;
    }

    /** Igual a carregarDono, mas trava a linha: serializa cliques duplos e ações concorrentes. */
    @Transactional
    public Reserva carregarDonoParaEscrita(String codigo, Solicitante s) {
        Reserva r = reservas.findParaAtualizarByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> Util.naoEncontrado("Reserva"));
        verificarDono(r, s);
        return r;
    }

    @Transactional(readOnly = true)
    public Reserva carregarPorId(UUID id) {
        return reservas.findById(id).orElseThrow(() -> Util.naoEncontrado("Reserva"));
    }

    /**
     * Dono = usuário logado da reserva OU pedido com o e-mail do responsável. Sem isso, trocar o código vazaria
     * reservas. A equipe do hotel (ADMIN) pode agir em qualquer reserva.
     */
    public void verificarDono(Reserva r, Solicitante s) {
        if (s.admin()) return;
        boolean donoConta = r.getUsuarioId() != null && r.getUsuarioId().equals(s.usuarioId());
        boolean donoEmail = s.email() != null && r.getResponsavelEmail() != null
                && r.getResponsavelEmail().equalsIgnoreCase(s.email().trim());
        if (!donoConta && !donoEmail) throw Util.semPermissao();
    }

    /** EXPIRADA (ou PENDENTE vencida) = 410. Estado fora da lista permitida = 422. */
    public void exigirStatus(Reserva r, StatusReserva... permitidos) {
        boolean vencida = r.getStatus() == StatusReserva.PENDENTE && r.getExpiraEm().isBefore(Util.agora());
        if (r.getStatus() == StatusReserva.EXPIRADA || vencida) {
            throw new ApiException(HttpStatus.GONE, "RESERVA_EXPIRADA", "Uga! O tempo da toca acabou.",
                    "O tempo desta reserva venceu e a toca foi liberada. Nada foi cobrado. Faça uma nova busca para reservar de novo.");
        }
        if (!Arrays.asList(permitidos).contains(r.getStatus())) {
            throw Util.estadoInvalido("A reserva está " + r.getStatus() + " e essa ação não é possível nesse estado.");
        }
    }

    public void mudarStatus(Reserva r, StatusReserva novo) {
        if (!TRANSICOES.get(r.getStatus()).contains(novo)) {
            throw Util.estadoInvalido("A reserva está " + r.getStatus() + " e não pode ir para " + novo + ".");
        }
        r.setStatus(novo);
        r.setAtualizadoEm(Util.agora());
    }

    @Transactional(readOnly = true)
    public ReservaRes toRes(Reserva r) {
        Caverna c = cavernas.findById(r.getCavernaId()).orElseThrow(() -> Util.naoEncontrado("Caverna"));
        String unidade = unidades.findById(r.getUnidadeId()).map(UnidadeCaverna::getCodigo).orElse(null);
        List<HospedeDto> hs = hospedes.findByReservaId(r.getId()).stream()
                .map(h -> new HospedeDto(h.getNome(), h.getTipo(), h.getIdade())).toList();
        List<ReservaExtra> res = reservaExtras.findByReservaId(r.getId());
        Map<String, String> nomes = extras.findAllById(res.stream().map(ReservaExtra::getExtraId).toList()).stream()
                .collect(Collectors.toMap(Extra::getId, Extra::getNome));
        List<ExtraSelecionadoRes> exs = res.stream().map(e -> new ExtraSelecionadoRes(e.getExtraId(),
                nomes.getOrDefault(e.getExtraId(), e.getExtraId()), e.getQuantidade(),
                e.getPrecoUnitarioCentavos(), e.getSubtotalCentavos())).toList();
        String cupom = r.getCupomId() == null ? null : cupons.findById(r.getCupomId()).map(Cupom::getCodigo).orElse(null);
        boolean pago = pagamentos.findByReservaId(r.getId()).stream().anyMatch(p -> p.getStatus() == StatusPagamento.APROVADO);
        return new ReservaRes(r.getCodigo(), r.getStatus(), c.getId(), c.getNome(), unidade, r.getCheckIn(), r.getCheckOut(),
                noites(r), r.getAdultos(), r.getCriancas(), r.getExpiraEm(), r.isProrrogada(), r.getResponsavelNome(),
                r.getResponsavelEmail(), r.getResponsavelCelular(), hs, exs, comboRes(r), cupom, r.getPedidoEspecial(),
                r.isLateCheckout(), r.getValorDiarias(), r.getValorExtras(), r.getValorCombo(), r.getDesconto(),
                r.getTotal(), pago, r.getCheckinOnlineEm());
    }

    // =====================================================================
    // Internos
    // =====================================================================

    private int noites(Reserva r) { return (int) ChronoUnit.DAYS.between(r.getCheckIn(), r.getCheckOut()); }

    private String codigoUnico() {
        for (int i = 0; i < 20; i++) {
            String c = Util.codigo("HB");
            if (reservas.findByCodigo(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código de reserva único.");
    }

    private void validarCupom(Cupom c) {
        boolean ok = c.isAtivo()
                && (c.getValidoAte() == null || !c.getValidoAte().isBefore(Util.hoje()))
                && (c.getUsosMaximos() == null || c.getUsos() < c.getUsosMaximos());
        if (!ok) throw cupomInvalido();
    }

    private ApiException cupomInvalido() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CUPOM_INVALIDO", "Uga! Esse cupom não vale.",
                "O cupom não existe, venceu ou já esgotou. Nenhum desconto foi aplicado e o total continua o mesmo. Confira o código e tente de novo.");
    }

    private ApiException semDisponibilidade(Caverna c, LocalDate in, LocalDate out) {
        return new ApiException(HttpStatus.CONFLICT, "SEM_DISPONIBILIDADE", "Ugh! Essa toca acabou de ser ocupada.",
                "Outra tribo ficou com as datas escolhidas enquanto você decidia. Nenhuma reserva foi alterada e nada foi cobrado. Veja datas próximas com toca livre.",
                Map.of("sugestoes", cavernaService.sugerir(List.of(c), in, out)));
    }

    // ---------- combo ----------

    private ComboBanquete combo(String id) {
        return combos.findById(id).filter(ComboBanquete::isAtivo).orElseThrow(() -> Util.naoEncontrado("O combo " + id));
    }

    /** Pessoas que pagam o combo: quem tem mais de 12 anos paga como adulto; 6 a 12, como criança; até 5, nada. */
    private int[] pagantesCombo(Reserva r) {
        List<HospedeReserva> hs = hospedes.findByReservaId(r.getId());
        if (hs.isEmpty()) return new int[]{r.getAdultos(), r.getCriancas()};
        int adultos = 0, criancas = 0;
        for (HospedeReserva h : hs) {
            if (h.getTipo() == TipoHospede.ADULTO || h.getIdade() > IDADE_CRIANCA_MAX) adultos++;
            else if (h.getIdade() > IDADE_GRATIS_MAX) criancas++;
        }
        return new int[]{adultos, criancas};
    }

    private int valorCombo(Reserva r, ComboBanquete c) {
        int[] p = pagantesCombo(r);
        return noites(r) * (p[0] * c.getPrecoAdultoCentavos() + p[1] * c.getPrecoCriancaCentavos());
    }

    /**
     * (Re)cria uma mesa por noite da estadia no horário do combo, para a tribo inteira. As mesas antigas do combo
     * são canceladas antes, para não contarem duas vezes na lotação. Confere TODAS as noites antes de gravar.
     */
    private void reservarMesasCombo(Reserva r, StatusMesa status) {
        cancelarMesasCombo(r);
        mesas.flush();
        int pessoas = r.getAdultos() + r.getCriancas();
        LocalTime horario = r.getComboHorario();
        List<LocalDate> noites = r.getCheckIn().datesUntil(r.getCheckOut()).toList();
        if (noites.getFirst().equals(Util.hoje()) && !horario.isAfter(LocalTime.now(Util.FUSO))) {
            throw Util.validacao("O horário do jantar de hoje já passou. Escolha um horário mais tarde.");
        }
        for (LocalDate d : noites) ocupacaoBanquete.garantirLugares(d, horario, pessoas);
        for (LocalDate d : noites) {
            ReservaMesa m = new ReservaMesa();
            m.setCodigo(codigoMesaUnico());
            m.setUsuarioId(r.getUsuarioId());
            m.setReservaId(r.getId());
            m.setOrigem(OrigemMesa.COMBO);
            m.setNomeContato(r.getResponsavelNome());
            m.setEmailContato(r.getResponsavelEmail());
            m.setCelularContato(r.getResponsavelCelular());
            m.setData(d);
            m.setHorario(horario);
            m.setPessoas(pessoas);
            m.setStatus(status);
            m.setCriadoEm(Util.agora());
            mesas.save(m);
        }
    }

    private void cancelarMesasCombo(Reserva r) {
        mesas.findByReservaIdAndOrigem(r.getId(), OrigemMesa.COMBO).stream()
                .filter(m -> m.getStatus() == StatusMesa.PENDENTE || m.getStatus() == StatusMesa.CONFIRMADA)
                .forEach(m -> { m.setStatus(StatusMesa.CANCELADA); mesas.save(m); });
    }

    private ComboSelecionadoRes comboRes(Reserva r) {
        if (r.getComboId() == null) return null;
        ComboBanquete c = combos.findById(r.getComboId()).orElse(null);
        int[] p = pagantesCombo(r);
        List<String> codigos = mesas.findByReservaIdAndOrigem(r.getId(), OrigemMesa.COMBO).stream()
                .filter(m -> m.getStatus() != StatusMesa.CANCELADA)
                .sorted(Comparator.comparing(ReservaMesa::getData)).map(ReservaMesa::getCodigo).toList();
        return new ComboSelecionadoRes(r.getComboId(), c == null ? r.getComboId() : c.getNome(), r.getComboHorario(),
                noites(r), p[0], p[1], r.getValorCombo(), codigos);
    }

    private String codigoMesaUnico() {
        for (int i = 0; i < 20; i++) {
            String c = Util.codigo("BQ");
            if (mesas.findByCodigo(c).isEmpty()) return c;
        }
        throw new IllegalStateException("Não foi possível gerar um código de mesa único.");
    }

    /** Ao cancelar a estadia: devolve vagas de atividades e cancela mesas do Banquete (avulsas e do combo). */
    private void cancelarDependencias(Reserva r) {
        for (AgendamentoAtividade a : agendamentos.findByReservaId(r.getId())) {
            if (a.getStatus() == StatusAgendamento.ATIVO) {
                sessoes.liberarVagas(a.getSessaoId(), a.getQuantidade());
                a.setStatus(StatusAgendamento.CANCELADO);
                agendamentos.save(a);
            }
        }
        for (ReservaMesa m : mesas.findByReservaId(r.getId())) {
            if (m.getStatus() == StatusMesa.CONFIRMADA || m.getStatus() == StatusMesa.PENDENTE) {
                m.setStatus(StatusMesa.CANCELADA);
                mesas.save(m);
            }
        }
    }
}
