package com.hugabuga.api.service;

import com.hugabuga.api.dto.CavernaDtos.CavernaCardRes;
import com.hugabuga.api.dto.ContaDtos.*;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Perfil, preferências, tribo, favoritos, pagamentos e "Minhas reservas". */
@Service
@RequiredArgsConstructor
public class ContaService {

    private final UsuarioRepository usuarios;
    private final MembroTriboRepository membros;
    private final FavoritoRepository favoritos;
    private final CavernaRepository cavernas;
    private final FotoCavernaRepository fotos;
    private final PagamentoRepository pagamentos;
    private final RelatoRepository relatos;
    private final ReservaRepository reservas;
    private final ReservaMesaRepository mesas;
    private final PasswordEncoder encoder;
    private final AuthService authService;
    private final CavernaService cavernaService;

    // ---------- perfil ----------

    @Transactional(readOnly = true)
    public PerfilRes perfil(UUID usuarioId) { return toPerfil(buscar(usuarioId)); }

    @Transactional
    public PerfilRes atualizar(UUID usuarioId, PerfilReq r) {
        Usuario u = buscar(usuarioId);
        if (r.nome() != null && !r.nome().isBlank()) u.setNome(r.nome().trim());
        if (r.sobrenome() != null && !r.sobrenome().isBlank()) u.setSobrenome(r.sobrenome().trim());
        if (r.celular() != null && !r.celular().isBlank()) u.setCelular(r.celular());
        if (r.pais() != null && r.pais().length() == 2) u.setPais(r.pais().toUpperCase());
        if (r.tipoDocumento() != null && !r.tipoDocumento().isBlank()) u.setTipoDocumento(r.tipoDocumento().toUpperCase());
        if (r.numeroDocumento() != null && !r.numeroDocumento().isBlank()) u.setNumeroDocumento(r.numeroDocumento());
        if (r.dataNascimento() != null) {
            if (r.dataNascimento().isAfter(Util.hoje())) throw Util.validacao("A data de nascimento está no futuro.");
            u.setDataNascimento(r.dataNascimento());
        }
        if (r.aceitaOfertas() != null) u.setAceitaOfertas(r.aceitaOfertas());
        return toPerfil(usuarios.save(u));
    }

    @Transactional
    public void trocarSenha(UUID usuarioId, SenhaReq req) {
        Usuario u = buscar(usuarioId);
        if (!encoder.matches(req.senhaAtual(), u.getSenhaHash())) throw Util.validacao("A senha atual está incorreta.");
        AuthService.validarSenha(req.novaSenha());
        u.setSenhaHash(encoder.encode(req.novaSenha()));
        usuarios.save(u);
        authService.revogarTodos(usuarioId); // derruba todas as sessões
    }

    // ---------- preferências ----------

    @Transactional(readOnly = true)
    public PreferenciasRes preferencias(UUID usuarioId) { return toPreferencias(buscar(usuarioId)); }

    @Transactional
    public PreferenciasRes atualizarPreferencias(UUID usuarioId, PreferenciasReq r) {
        Usuario u = buscar(usuarioId);
        if (r.idioma() != null) u.setIdioma(r.idioma());
        if (r.notificacoesEmail() != null) u.setNotificacoesEmail(r.notificacoesEmail());
        if (r.notificacoesWhatsapp() != null) u.setNotificacoesWhatsapp(r.notificacoesWhatsapp());
        if (r.aceitaOfertas() != null) u.setAceitaOfertas(r.aceitaOfertas());
        return toPreferencias(usuarios.save(u));
    }

    // ---------- membros da tribo ----------

    @Transactional(readOnly = true)
    public List<MembroRes> membros(UUID usuarioId) {
        return membros.findByUsuarioId(usuarioId).stream().map(this::toRes).toList();
    }

    @Transactional
    public MembroRes adicionarMembro(UUID usuarioId, MembroReq req) {
        MembroTribo m = new MembroTribo();
        m.setUsuarioId(usuarioId);
        aplicar(m, req);
        return toRes(membros.save(m));
    }

    @Transactional
    public MembroRes editarMembro(UUID usuarioId, UUID id, MembroReq req) {
        MembroTribo m = membros.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> Util.naoEncontrado("Membro da tribo"));
        aplicar(m, req);
        return toRes(membros.save(m));
    }

    @Transactional
    public void removerMembro(UUID usuarioId, UUID id) {
        MembroTribo m = membros.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> Util.naoEncontrado("Membro da tribo"));
        membros.delete(m);
    }

    // ---------- favoritos ----------

    @Transactional(readOnly = true)
    public List<CavernaCardRes> favoritos(UUID usuarioId) {
        List<UUID> ids = favoritos.findByUsuarioId(usuarioId).stream().map(Favorito::getCavernaId).toList();
        return cavernaService.cardsPorIds(ids);
    }

    @Transactional
    public void favoritar(UUID usuarioId, UUID cavernaId) {
        if (!cavernas.existsById(cavernaId)) throw Util.naoEncontrado("Caverna");
        if (!favoritos.existsByUsuarioIdAndCavernaId(usuarioId, cavernaId)) { // idempotente
            favoritos.save(new Favorito(usuarioId, cavernaId));
        }
    }

    @Transactional
    public void desfavoritar(UUID usuarioId, UUID cavernaId) {
        favoritos.deleteByUsuarioIdAndCavernaId(usuarioId, cavernaId);
    }

    // ---------- pagamentos ----------

    /** Histórico de pagamentos das reservas da conta (nunca há cartão salvo: só final e bandeira). */
    @Transactional(readOnly = true)
    public List<PagamentoResumoRes> pagamentos(UUID usuarioId) {
        Map<UUID, String> codigos = reservas.findByUsuarioId(usuarioId).stream()
                .collect(Collectors.toMap(Reserva::getId, Reserva::getCodigo));
        if (codigos.isEmpty()) return List.of();
        return pagamentos.findByReservaIdInOrderByCriadoEmDesc(codigos.keySet()).stream()
                .map(p -> new PagamentoResumoRes(p.getId(), codigos.get(p.getReservaId()), p.getMetodo(), p.getStatus(),
                        p.getValorCentavos(), p.getParcelas(), p.getCartaoFinal(), p.getCartaoBandeira(),
                        p.getCriadoEm(), p.getAprovadoEm()))
                .toList();
    }

    // ---------- minhas reservas ----------

    private static final List<String> ABAS = List.of("proximas", "passadas", "canceladas");

    /** Abas Próximas / Passadas / Canceladas, com a contagem de cada uma. PENDENTE e EXPIRADA nunca aparecem. */
    @Transactional(readOnly = true)
    public MinhasReservasRes minhasReservas(UUID usuarioId, String aba) {
        if (aba == null || !ABAS.contains(aba)) throw Util.validacao("A aba precisa ser proximas, passadas ou canceladas.");
        LocalDate hoje = Util.hoje();
        List<Reserva> todas = reservas.findByUsuarioId(usuarioId);
        // mesas do combo aparecem dentro da estadia, não soltas na lista
        List<ReservaMesa> todasMesas = mesas.findByUsuarioId(usuarioId).stream()
                .filter(m -> m.getOrigem() != OrigemMesa.COMBO).toList();

        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (String a : ABAS) {
            contagem.put(a, (int) (todas.stream().filter(r -> abaDa(r, hoje).equals(a)).count()
                    + todasMesas.stream().filter(m -> abaDa(m, hoje).equals(a)).count()));
        }
        List<Reserva> estadias = todas.stream().filter(r -> abaDa(r, hoje).equals(aba))
                .sorted(Comparator.comparing(Reserva::getCheckIn)).toList();
        List<ReservaMesa> mesasAba = todasMesas.stream().filter(m -> abaDa(m, hoje).equals(aba))
                .sorted(Comparator.comparing(ReservaMesa::getData).thenComparing(ReservaMesa::getHorario)).toList();

        List<UUID> cavernaIds = estadias.stream().map(Reserva::getCavernaId).distinct().toList();
        Map<UUID, Caverna> cavs = cavernas.findAllById(cavernaIds).stream()
                .collect(Collectors.toMap(Caverna::getId, Function.identity()));
        Map<UUID, String> capas = cavernaIds.isEmpty() ? Map.of() : fotos.findByCavernaIdInOrderByOrdem(cavernaIds).stream()
                .collect(Collectors.toMap(FotoCaverna::getCavernaId, FotoCaverna::getUrl, (a, b) -> a));
        Set<UUID> pagas = estadias.isEmpty() ? Set.of()
                : pagamentos.findByReservaIdInOrderByCriadoEmDesc(estadias.stream().map(Reserva::getId).toList()).stream()
                .filter(p -> p.getStatus() == StatusPagamento.APROVADO).map(Pagamento::getReservaId).collect(Collectors.toSet());

        List<ReservaResumoRes> resEstadias = estadias.stream().map(r -> {
            Caverna c = cavs.get(r.getCavernaId());
            long dias = ChronoUnit.DAYS.between(hoje, r.getCheckIn());
            boolean podeAvaliar = r.getStatus() == StatusReserva.CONCLUIDA && !relatos.existsByReservaId(r.getId());
            return new ReservaResumoRes(r.getCodigo(), r.getCavernaId(), c == null ? null : c.getNome(),
                    c == null ? null : c.getSlug(), capas.get(r.getCavernaId()), r.getCheckIn(), r.getCheckOut(),
                    (int) ChronoUnit.DAYS.between(r.getCheckIn(), r.getCheckOut()), r.getAdultos(), r.getCriancas(),
                    r.getStatus(), r.getTotal(), pagas.contains(r.getId()), dias >= 0 ? (int) dias : null,
                    r.getComboId() != null, podeAvaliar);
        }).toList();
        List<MesaResumoRes> resMesas = mesasAba.stream()
                .map(m -> new MesaResumoRes(m.getCodigo(), m.getData(), m.getHorario(), m.getPessoas(), m.getStatus(),
                        m.getOrigem())).toList();
        return new MinhasReservasRes(resEstadias, resMesas, contagem);
    }

    /** Em qual aba a estadia aparece ("" = nenhuma). */
    private static String abaDa(Reserva r, LocalDate hoje) {
        return switch (r.getStatus()) {
            case CONFIRMADA -> r.getCheckOut().isBefore(hoje) ? "passadas" : "proximas";
            case AGUARDANDO_PAGAMENTO -> r.getCheckOut().isBefore(hoje) ? "" : "proximas";
            case CONCLUIDA -> "passadas";
            case CANCELADA -> "canceladas";
            case PENDENTE, EXPIRADA -> "";
        };
    }

    private static String abaDa(ReservaMesa m, LocalDate hoje) {
        return switch (m.getStatus()) {
            case CONFIRMADA -> m.getData().isBefore(hoje) ? "passadas" : "proximas";
            case NAO_COMPARECEU -> "passadas";
            case CANCELADA -> "canceladas";
            case PENDENTE -> "";
        };
    }

    // ---------- internos ----------

    private Usuario buscar(UUID id) { return usuarios.findById(id).orElseThrow(Util::naoAutenticado); }

    private PerfilRes toPerfil(Usuario u) {
        return new PerfilRes(u.getId(), u.getNome(), u.getSobrenome(), u.getEmail(), u.getCelular(), u.getPais(),
                u.getTipoDocumento(), mascarar(u.getNumeroDocumento()), u.getDataNascimento(),
                u.isAceitaOfertas(), u.getRole(), u.getIdioma());
    }

    private PreferenciasRes toPreferencias(Usuario u) {
        return new PreferenciasRes(u.getIdioma(), u.isNotificacoesEmail(), u.isNotificacoesWhatsapp(), u.isAceitaOfertas());
    }

    /** Só os 4 últimos caracteres saem na API (LGPD). */
    static String mascarar(String doc) {
        if (doc == null || doc.length() <= 4) return doc == null ? null : "••••";
        return "•••• " + doc.substring(doc.length() - 4);
    }

    private void aplicar(MembroTribo m, MembroReq r) {
        if (r.dataNascimento() != null && r.dataNascimento().isAfter(Util.hoje())) {
            throw Util.validacao("A data de nascimento está no futuro.");
        }
        if (r.tipo() == TipoHospede.CRIANCA) {
            if (r.dataNascimento() == null) throw Util.validacao("Informe a data de nascimento da criança.");
            if (idade(r.dataNascimento()) >= 18) throw Util.validacao("Uma criança precisa ter menos de 18 anos.");
        }
        m.setNome(r.nome().trim());
        m.setTipo(r.tipo());
        m.setDataNascimento(r.dataNascimento());
        m.setObservacoes(r.observacoes());
    }

    private MembroRes toRes(MembroTribo m) {
        // A idade é calculada na hora, nunca armazenada.
        Integer idade = m.getDataNascimento() == null ? null : idade(m.getDataNascimento());
        return new MembroRes(m.getId(), m.getNome(), m.getTipo(), m.getDataNascimento(), idade, m.getObservacoes());
    }

    private int idade(LocalDate nascimento) { return Period.between(nascimento, Util.hoje()).getYears(); }
}
