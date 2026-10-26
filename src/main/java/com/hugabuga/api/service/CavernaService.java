package com.hugabuga.api.service;

import com.hugabuga.api.dto.CavernaDtos.*;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Busca, disponibilidade, cotação, detalhe e relatos. */
@Service
@RequiredArgsConstructor
public class CavernaService {

    private static final int ADULTOS_INCLUSOS = 2;
    private static final int IDADE_GRATIS_MAX = 5;     // até 5 anos: grátis
    private static final int IDADE_CRIANCA_MAX = 12;   // 6 a 12: criança; acima disso conta como adulto
    private static final int POR_PAGINA_RELATOS = 10;

    private final CavernaRepository cavernas;
    private final UnidadeCavernaRepository unidades;
    private final FotoCavernaRepository fotos;
    private final ComodidadeRepository comodidades;
    private final TarifaEspecialRepository tarifas;
    private final RelatoRepository relatos;
    private final UsuarioRepository usuarios;

    @Value("${hugabuga.regras.adulto-extra-centavos:6000}")
    private int adultoExtraCentavos;

    private record Candidata(Caverna c, Integer livres, Integer total) {
        int preco() { return total != null ? total : c.getPrecoBaseCentavos(); }
    }

    // ---------- busca ----------

    @Transactional(readOnly = true)
    public BuscaRes buscar(BuscaFiltro f) {
        if ((f.checkIn() == null) != (f.checkOut() == null)) throw Util.validacao("Informe check-in e check-out juntos.");
        boolean comDatas = f.checkIn() != null;
        int adultos = f.adultos() == null ? 2 : f.adultos();
        List<Integer> idades = f.idadesCriancas() == null ? List.of() : f.idadesCriancas();
        if (comDatas) validarDatas(f.checkIn(), f.checkOut());
        validarPessoas(adultos, idades);

        List<String> filtros = new ArrayList<>();
        if (f.categoria() != null && !f.categoria().isEmpty()) filtros.add("categoria: " + f.categoria());
        if (f.precoMin() != null || f.precoMax() != null) filtros.add("preço");
        if (f.comodidades() != null && !f.comodidades().isEmpty()) filtros.add("comodidades: " + f.comodidades());
        if (Boolean.TRUE.equals(f.acessivel())) filtros.add("acessível");

        // Catálogo pequeno (poucas cavernas): filtramos em memória e paginamos no final.
        List<Caverna> candidatas = cavernas.findByAtivaTrue().stream()
                .filter(c -> passaFiltros(c, f, adultos, idades)).toList();

        List<Candidata> itens = new ArrayList<>();
        boolean feriado = false;
        for (Caverna c : candidatas) {
            if (!comDatas) { itens.add(new Candidata(c, null, null)); continue; }
            int livres = unidades.unidadesLivres(c.getId(), f.checkIn(), f.checkOut()).size();
            if (livres == 0) continue;
            CotacaoRes cot = calcular(c, f.checkIn(), f.checkOut(), adultos, idades);
            feriado |= cot.feriado();
            itens.add(new Candidata(c, livres, cot.valorDiariasCentavos()));
        }

        Comparator<Candidata> cmp = switch (f.ordem() == null ? "nota" : f.ordem()) {
            case "menor_preco" -> Comparator.comparingInt(Candidata::preco);
            case "maior_preco" -> Comparator.comparingInt(Candidata::preco).reversed();
            default -> Comparator.comparingDouble((Candidata x) -> x.c().getNotaMedia()).reversed();
        };
        itens.sort(cmp);

        int size = f.size() <= 0 ? 10 : Math.min(f.size(), 50);
        int page = Math.max(0, f.page());
        List<Candidata> pagina = itens.stream().skip((long) page * size).limit(size).toList();
        Map<UUID, String> fotoPorCaverna = primeiraFoto(pagina.stream().map(x -> x.c().getId()).toList());
        List<CavernaCardRes> cards = pagina.stream()
                .map(x -> toCard(x.c(), x.livres(), x.total(), fotoPorCaverna.get(x.c().getId()))).toList();

        List<String> avisos = new ArrayList<>();
        if (feriado) avisos.add("Feriado no período: valores especiais aplicados em algumas noites.");

        List<SugestaoData> sugestoes = (comDatas && itens.isEmpty())
                ? sugerir(candidatas, f.checkIn(), f.checkOut()) : List.of();
        return new BuscaRes(itens.size(), filtros, avisos, cards, sugestoes);
    }

    private boolean passaFiltros(Caverna c, BuscaFiltro f, int adultos, List<Integer> idades) {
        if (f.categoria() != null && !f.categoria().isEmpty() && !f.categoria().contains(c.getCategoria())) return false;
        if (Boolean.TRUE.equals(f.acessivel()) && !c.isAcessivel()) return false;
        if (f.precoMin() != null && c.getPrecoBaseCentavos() < f.precoMin()) return false;
        if (f.precoMax() != null && c.getPrecoBaseCentavos() > f.precoMax()) return false;
        if (f.comodidades() != null && !f.comodidades().isEmpty()) {
            Set<String> ids = c.getComodidades().stream().map(Comodidade::getId).collect(Collectors.toSet());
            if (!ids.containsAll(f.comodidades())) return false;
        }
        return pessoasQueOcupam(adultos, idades) <= c.getCapacidade();
    }

    /** Sugere datas próximas (2 dias depois, semana seguinte) com ao menos uma toca livre. */
    public List<SugestaoData> sugerir(List<Caverna> candidatas, LocalDate in, LocalDate out) {
        long noites = ChronoUnit.DAYS.between(in, out);
        Map<Integer, String> opcoes = new LinkedHashMap<>();
        opcoes.put(2, "2 dias depois");
        opcoes.put(7, "Na semana seguinte");
        List<SugestaoData> r = new ArrayList<>();
        opcoes.forEach((dias, rotulo) -> {
            LocalDate ni = in.plusDays(dias), no = ni.plusDays(noites);
            boolean livre = candidatas.stream().anyMatch(c -> !unidades.unidadesLivres(c.getId(), ni, no).isEmpty());
            if (livre) r.add(new SugestaoData(ni, no, rotulo));
        });
        return r;
    }

    // ---------- cotação ----------

    @Transactional(readOnly = true)
    public CotacaoRes cotar(UUID cavernaId, LocalDate in, LocalDate out, int adultos, List<Integer> idadesCriancas) {
        Caverna c = cavernas.findById(cavernaId).filter(Caverna::isAtiva).orElseThrow(() -> Util.naoEncontrado("Caverna"));
        return calcular(c, in, out, adultos, idadesCriancas == null ? List.of() : idadesCriancas);
    }

    /**
     * Regras de preço: noite = tarifa especial da data ou preço base; diária cobre até 2 adultos;
     * cada adulto a mais soma a taxa por noite; criança > 12 anos conta como adulto;
     * capacidade considera só pessoas acima de 5 anos.
     */
    private CotacaoRes calcular(Caverna c, LocalDate in, LocalDate out, int adultos, List<Integer> idades) {
        validarDatas(in, out);
        validarPessoas(adultos, idades);

        int adultosCobrados = adultos + (int) idades.stream().filter(i -> i > IDADE_CRIANCA_MAX).count();
        int criancas6a12 = (int) idades.stream().filter(i -> i > IDADE_GRATIS_MAX && i <= IDADE_CRIANCA_MAX).count();
        int gratis = (int) idades.stream().filter(i -> i <= IDADE_GRATIS_MAX).count();
        if (adultosCobrados + criancas6a12 > c.getCapacidade()) {
            throw Util.validacao("A " + c.getNome() + " comporta até " + c.getCapacidade()
                    + " pessoas acima de 5 anos, e o grupo tem " + (adultosCobrados + criancas6a12) + ".");
        }
        int adultosExtras = Math.max(0, adultosCobrados - ADULTOS_INCLUSOS);

        Map<LocalDate, Integer> especiais = tarifas
                .findByCavernaIdAndDataBetween(c.getId(), in, out.minusDays(1)).stream()
                .collect(Collectors.toMap(TarifaEspecial::getData, TarifaEspecial::getPrecoCentavos, (a, b) -> a));

        List<NoiteValor> detalhe = new ArrayList<>();
        int soma = 0;
        for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1)) {
            int preco = especiais.getOrDefault(d, c.getPrecoBaseCentavos());
            int adicional = adultosExtras * adultoExtraCentavos;
            detalhe.add(new NoiteValor(d, preco, adicional));
            soma += preco + adicional;
        }
        return new CotacaoRes(detalhe.size(), adultosCobrados, adultosExtras, gratis, soma, detalhe, !especiais.isEmpty());
    }

    // ---------- detalhe, relatos, comodidades ----------

    @Transactional(readOnly = true)
    public CavernaDetalheRes detalhe(String slug) {
        Caverna c = cavernas.findBySlugAndAtivaTrue(slug).orElseThrow(() -> Util.naoEncontrado("Caverna"));
        List<FotoCaverna> fs = fotos.findByCavernaIdOrderByOrdem(c.getId());
        String capa = fs.isEmpty() ? null : fs.get(0).getUrl();
        return new CavernaDetalheRes(toCard(c, null, null, capa), c.getDescricao(),
                fs.stream().map(x -> new FotoRes(x.getUrl(), x.getLegenda())).toList(),
                c.getComodidades().stream().map(this::toRes).toList());
    }

    @Transactional(readOnly = true)
    public PaginaRes<RelatoRes> relatos(UUID cavernaId, int page) {
        if (!cavernas.existsById(cavernaId)) throw Util.naoEncontrado("Caverna");
        int p = Math.max(0, page);
        Page<Relato> pg = relatos.findByCavernaIdOrderByCriadoEmDesc(cavernaId, PageRequest.of(p, POR_PAGINA_RELATOS));
        Map<UUID, Usuario> autores = usuarios.findAllById(pg.stream().map(Relato::getUsuarioId).distinct().toList())
                .stream().collect(Collectors.toMap(Usuario::getId, Function.identity()));
        List<RelatoRes> itens = pg.stream().map(r -> {
            Usuario u = autores.get(r.getUsuarioId());
            String autor = u == null ? "Hóspede" : u.getNome() + " " + u.getSobrenome().charAt(0) + ".";
            return new RelatoRes(r.getNota(), r.getTexto(), autor, r.getCriadoEm());
        }).toList();
        return new PaginaRes<>(itens, p, POR_PAGINA_RELATOS, pg.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<ComodidadeRes> comodidades() {
        return comodidades.findAll().stream().map(this::toRes).toList();
    }

    @Transactional(readOnly = true)
    public List<CavernaCardRes> cardsPorIds(List<UUID> ids) {
        Map<UUID, String> capa = primeiraFoto(ids);
        return cavernas.findAllById(ids).stream()
                .map(c -> toCard(c, null, null, capa.get(c.getId()))).toList();
    }

    /** Chamado quando entra um novo relato: guarda a média no próprio registro (evita agregar a cada busca). */
    @Transactional
    public void recalcularNota(UUID cavernaId) {
        Caverna c = cavernas.findById(cavernaId).orElseThrow(() -> Util.naoEncontrado("Caverna"));
        Double media = relatos.mediaNota(cavernaId);
        c.setNotaMedia(media == null ? 0 : BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP).doubleValue());
        c.setTotalRelatos((int) relatos.countByCavernaId(cavernaId));
        cavernas.save(c);
    }

    // ---------- internos ----------

    private void validarDatas(LocalDate in, LocalDate out) {
        if (!out.isAfter(in)) throw Util.validacao("O check-out precisa ser depois do check-in.");
        if (in.isBefore(Util.hoje())) throw Util.validacao("O check-in não pode estar no passado.");
    }

    private void validarPessoas(int adultos, List<Integer> idades) {
        if (adultos < 1) throw Util.validacao("É preciso ao menos 1 adulto.");
        if (idades.stream().anyMatch(i -> i == null || i < 0 || i > 17)) {
            throw Util.validacao("A idade das crianças deve estar entre 0 e 17 anos.");
        }
    }

    private int pessoasQueOcupam(int adultos, List<Integer> idades) {
        return adultos + (int) idades.stream().filter(i -> i > IDADE_GRATIS_MAX).count();
    }

    private Map<UUID, String> primeiraFoto(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return fotos.findByCavernaIdInOrderByOrdem(ids).stream()
                .collect(Collectors.toMap(FotoCaverna::getCavernaId, FotoCaverna::getUrl, (a, b) -> a)); // a 1ª (menor ordem) vence
    }

    private CavernaCardRes toCard(Caverna c, Integer livres, Integer total, String foto) {
        return new CavernaCardRes(c.getId(), c.getSlug(), c.getNome(), c.getCategoria(), c.getAla(), c.getResumo(),
                c.getCapacidade(), c.getCamas(), c.getAreaM2(), c.getPrecoBaseCentavos(), total,
                c.getNotaMedia(), c.getTotalRelatos(), c.isAcessivel(), c.isAceitaPets(), foto,
                livres, livres != null && livres <= 2, // selo "Últimas 2!"
                c.getComodidades().stream().map(Comodidade::getId).toList());
    }

    private ComodidadeRes toRes(Comodidade m) { return new ComodidadeRes(m.getId(), m.getNome(), m.getIcone()); }
}
