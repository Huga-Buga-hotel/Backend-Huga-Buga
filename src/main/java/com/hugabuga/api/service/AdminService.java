package com.hugabuga.api.service;

import com.hugabuga.api.dto.AdminDtos.*;
import com.hugabuga.api.dto.CavernaDtos.PaginaRes;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Painel da equipe do hotel (role ADMIN): ocupação, reservas, tocas, cavernas, Banquete, avaliações e usuários.
 * Cancelar, ver voucher e fazer check-in de qualquer reserva usam as rotas normais de /reservas: o ADMIN passa
 * na conferência de dono (ReservaService.verificarDono).
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int TAMANHO_MAX_PAGINA = 100;

    private final ReservaRepository reservas;
    private final CavernaRepository cavernas;
    private final UnidadeCavernaRepository unidades;
    private final PagamentoRepository pagamentos;
    private final ReservaMesaRepository mesas;
    private final UsuarioRepository usuarios;
    private final OcupacaoBanqueteService ocupacaoBanquete;

    // ---------- painel ----------

    @Transactional(readOnly = true)
    public DashboardRes dashboard(LocalDate data) {
        LocalDate dia = data != null ? data : Util.hoje();
        Set<UUID> ativas = unidades.findAll().stream().filter(UnidadeCaverna::isAtiva)
                .map(UnidadeCaverna::getId).collect(Collectors.toSet());
        long ocupadas = reservas.ativasNaNoite(dia).stream().map(Reserva::getUnidadeId)
                .filter(ativas::contains).distinct().count();
        int pct = ativas.isEmpty() ? 0 : (int) Math.round(100.0 * ocupadas / ativas.size());

        LocalDate inicioMes = dia.withDayOfMonth(1);
        long receita = pagamentos.somaAprovados(inicioMes.atStartOfDay(Util.FUSO).toOffsetDateTime(),
                inicioMes.plusMonths(1).atStartOfDay(Util.FUSO).toOffsetDateTime());
        int pessoasBanquete = mesas.ocupandoNoDia(dia).stream()
                .filter(m -> m.getStatus() == StatusMesa.CONFIRMADA).mapToInt(ReservaMesa::getPessoas).sum();

        return new DashboardRes(dia, ativas.size(), (int) ocupadas, pct,
                toAdmin(reservas.entradasDoDia(dia, StatusReserva.CONFIRMADA)),
                toAdmin(reservas.saidasDoDia(dia, StatusReserva.CONFIRMADA)),
                receita, pessoasBanquete, ocupacaoBanquete.capacidade());
    }

    // ---------- reservas ----------

    /** Lista com filtros opcionais: status, período (estadias que cruzam [de, ate)) e busca por código/e-mail/nome. */
    @Transactional(readOnly = true)
    public PaginaRes<ReservaAdminRes> reservas(StatusReserva status, LocalDate de, LocalDate ate, String busca,
                                               int page, int size) {
        Specification<Reserva> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (de != null) ps.add(cb.greaterThan(root.get("checkOut"), de));
            if (ate != null) ps.add(cb.lessThan(root.get("checkIn"), ate));
            if (busca != null && !busca.isBlank()) {
                String like = "%" + busca.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("codigo")), like),
                        cb.like(cb.lower(root.get("responsavelEmail")), like),
                        cb.like(cb.lower(root.get("responsavelNome")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        int p = Math.max(0, page), s = tamanho(size);
        Page<Reserva> pg = reservas.findAll(spec, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "criadoEm")));
        return new PaginaRes<>(toAdmin(pg.getContent()), p, s, pg.getTotalElements());
    }

    /** Situação de cada uma das 25 tocas em uma noite. */
    @Transactional(readOnly = true)
    public List<OcupacaoUnidadeRes> ocupacao(LocalDate data) {
        LocalDate noite = data != null ? data : Util.hoje();
        Map<UUID, Reserva> porUnidade = reservas.ativasNaNoite(noite).stream()
                .collect(Collectors.toMap(Reserva::getUnidadeId, Function.identity(), (a, b) -> a));
        Map<UUID, String> nomes = cavernas.findAll().stream().collect(Collectors.toMap(Caverna::getId, Caverna::getNome));
        return unidades.findAllByOrderByCodigo().stream().map(u -> {
            Reserva r = porUnidade.get(u.getId());
            String situacao = !u.isAtiva() ? "MANUTENCAO" : r != null ? "OCUPADA" : "LIVRE";
            return new OcupacaoUnidadeRes(u.getId(), u.getCodigo(), nomes.get(u.getCavernaId()), situacao,
                    r == null ? null : r.getCodigo(), r == null ? null : r.getStatus(),
                    r == null ? null : r.getCheckIn(), r == null ? null : r.getCheckOut(),
                    r == null ? null : r.getResponsavelNome());
        }).toList();
    }

    @Transactional
    public OcupacaoUnidadeRes alterarUnidade(UUID id, UnidadeReq req) {
        UnidadeCaverna u = unidades.findById(id).orElseThrow(() -> Util.naoEncontrado("Toca"));
        u.setAtiva(req.ativa());
        unidades.save(u);
        return ocupacao(Util.hoje()).stream().filter(x -> x.unidadeId().equals(id)).findFirst().orElseThrow();
    }

    // ---------- cavernas ----------

    @Transactional(readOnly = true)
    public List<CavernaAdminRes> cavernas() {
        List<UnidadeCaverna> todas = unidades.findAll();
        return cavernas.findAll().stream().sorted(Comparator.comparing(Caverna::getNome))
                .map(c -> toAdmin(c, todas)).toList();
    }

    @Transactional
    public CavernaAdminRes alterarCaverna(UUID id, CavernaAdminReq req) {
        Caverna c = cavernas.findById(id).orElseThrow(() -> Util.naoEncontrado("Caverna"));
        if (req.nome() != null && !req.nome().isBlank()) c.setNome(req.nome().trim());
        if (req.resumo() != null) c.setResumo(req.resumo().trim());
        if (req.descricao() != null) c.setDescricao(req.descricao().trim());
        if (req.precoBaseCentavos() != null) c.setPrecoBaseCentavos(req.precoBaseCentavos()); // reservas já feitas mantêm o preço congelado
        if (req.ativa() != null) c.setAtiva(req.ativa());
        cavernas.save(c);
        return toAdmin(c, unidades.findAll());
    }

    // ---------- Banquete ----------

    @Transactional(readOnly = true)
    public MesasDoDiaRes mesasDoDia(LocalDate data) {
        LocalDate dia = data != null ? data : Util.hoje();
        List<ReservaMesa> doDia = mesas.findByDataOrderByHorario(dia);
        Map<UUID, String> codigos = reservas.findAllById(doDia.stream().map(ReservaMesa::getReservaId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Reserva::getId, Reserva::getCodigo));
        int capacidade = ocupacaoBanquete.capacidade();
        List<OcupacaoHorarioRes> ocupacao = ocupacaoBanquete.ocupacaoPorHorario(dia).entrySet().stream()
                .map(e -> new OcupacaoHorarioRes(e.getKey(), e.getValue(), Math.max(0, capacidade - e.getValue())))
                .toList();
        List<MesaAdminRes> lista = doDia.stream().map(m -> new MesaAdminRes(m.getCodigo(), m.getOrigem(), m.getStatus(),
                m.getHorario(), m.getPessoas(), m.getCadeirinhas(), m.isPertoFogueira(), m.getNomeContato(),
                m.getCelularContato(), m.getEmailContato(),
                m.getReservaId() == null ? null : codigos.get(m.getReservaId()))).toList();
        return new MesasDoDiaRes(dia, capacidade, ocupacao, lista);
    }

    /** Marca que a mesa não apareceu (só depois do horário). Libera o lugar e vai para "Passadas". */
    @Transactional
    public void naoCompareceu(String codigo) {
        ReservaMesa m = mesas.findByCodigo(codigo == null ? "" : codigo.trim().toUpperCase())
                .orElseThrow(() -> Util.naoEncontrado("Reserva de mesa"));
        if (m.getStatus() != StatusMesa.CONFIRMADA) throw Util.estadoInvalido("Só mesas confirmadas podem ser marcadas.");
        if (Util.agora().isBefore(m.getData().atTime(m.getHorario()).atZone(Util.FUSO).toOffsetDateTime())) {
            throw Util.estadoInvalido("O horário da mesa ainda não chegou.");
        }
        m.setStatus(StatusMesa.NAO_COMPARECEU);
        mesas.save(m);
    }

    // ---------- usuários ----------

    @Transactional(readOnly = true)
    public PaginaRes<UsuarioAdminRes> usuarios(String busca, int page, int size) {
        String b = busca == null ? "" : busca.trim();
        int p = Math.max(0, page), s = tamanho(size);
        Page<Usuario> pg = usuarios.findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(b, b,
                PageRequest.of(p, s, Sort.by("nome")));
        return new PaginaRes<>(pg.map(this::toAdmin).getContent(), p, s, pg.getTotalElements());
    }

    /** Promove ou rebaixa. Vale a partir do próximo login/refresh do usuário. */
    @Transactional
    public UsuarioAdminRes alterarRole(UUID adminId, UUID usuarioId, RoleReq req) {
        if (adminId.equals(usuarioId) && req.role() != Role.ADMIN) {
            throw Util.estadoInvalido("Você não pode tirar o seu próprio acesso de administrador.");
        }
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> Util.naoEncontrado("Usuário"));
        u.setRole(req.role());
        return toAdmin(usuarios.save(u));
    }

    // ---------- mapeamento ----------

    private List<ReservaAdminRes> toAdmin(List<Reserva> lista) {
        if (lista.isEmpty()) return List.of();
        Map<UUID, String> nomes = cavernas.findAll().stream().collect(Collectors.toMap(Caverna::getId, Caverna::getNome));
        Map<UUID, String> codigosUnidade = unidades.findAll().stream()
                .collect(Collectors.toMap(UnidadeCaverna::getId, UnidadeCaverna::getCodigo));
        return lista.stream().map(r -> new ReservaAdminRes(r.getCodigo(), r.getStatus(), nomes.get(r.getCavernaId()),
                codigosUnidade.get(r.getUnidadeId()), r.getCheckIn(), r.getCheckOut(), r.getResponsavelNome(),
                r.getResponsavelEmail(), r.getAdultos(), r.getCriancas(), r.getTotal(), r.getComboId() != null,
                r.getCriadoEm())).toList();
    }

    private CavernaAdminRes toAdmin(Caverna c, List<UnidadeCaverna> todas) {
        List<UnidadeCaverna> doCaverna = todas.stream().filter(u -> u.getCavernaId().equals(c.getId())).toList();
        return new CavernaAdminRes(c.getId(), c.getSlug(), c.getNome(), c.getCategoria(), c.getCapacidade(),
                c.getPrecoBaseCentavos(), c.isAtiva(), doCaverna.size(),
                doCaverna.stream().filter(UnidadeCaverna::isAtiva).count(), c.getNotaMedia(), c.getTotalRelatos());
    }

    private UsuarioAdminRes toAdmin(Usuario u) {
        return new UsuarioAdminRes(u.getId(), u.getNome(), u.getSobrenome(), u.getEmail(), u.getRole(), u.getCriadoEm());
    }

    private static int tamanho(int size) { return size <= 0 ? 20 : Math.min(size, TAMANHO_MAX_PAGINA); }
}
