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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Perfil, tribo, favoritos, ossos e "Minhas reservas". */
@Service
@RequiredArgsConstructor
public class ContaService {

    private final UsuarioRepository usuarios;
    private final MembroTriboRepository membros;
    private final FavoritoRepository favoritos;
    private final MovimentoOssosRepository movimentos;
    private final CavernaRepository cavernas;
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

    // ---------- ossos ----------

    @Transactional(readOnly = true)
    public OssosRes ossos(UUID usuarioId) {
        Usuario u = buscar(usuarioId);
        // Nível usa o TOTAL ACUMULADO (só ganhos), não o saldo: o nível nunca cai.
        long acumulado = movimentos.somaGanhos(usuarioId);
        NivelFidelidade nivel = NivelFidelidade.de(acumulado);
        NivelFidelidade prox = nivel.proximo();
        List<MovimentoRes> extrato = movimentos.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .map(m -> new MovimentoRes(m.getQuantidade(), m.getMotivo().name(), m.getReservaId(), m.getCriadoEm()))
                .toList();
        return new OssosRes(u.getSaldoOssos(), acumulado, nivel.getNome(),
                prox == null ? null : prox.getNome(),
                prox == null ? 0 : prox.getMinimo() - acumulado, extrato);
    }

    // ---------- minhas reservas ----------

    @Transactional(readOnly = true)
    public MinhasReservasRes minhasReservas(UUID usuarioId, String aba) {
        LocalDate hoje = Util.hoje();
        List<Reserva> todas = reservas.findByUsuarioId(usuarioId);
        List<ReservaMesa> todasMesas = mesas.findByUsuarioId(usuarioId);

        List<Reserva> estadias;
        List<ReservaMesa> mesasAba;
        switch (aba == null ? "" : aba) {
            case "proximas" -> {
                estadias = todas.stream().filter(r -> (r.getStatus() == StatusReserva.CONFIRMADA
                        || r.getStatus() == StatusReserva.AGUARDANDO_PAGAMENTO) && !r.getCheckOut().isBefore(hoje)).toList();
                mesasAba = todasMesas.stream().filter(m -> m.getStatus() == StatusMesa.CONFIRMADA && !m.getData().isBefore(hoje)).toList();
            }
            case "passadas" -> {
                estadias = todas.stream().filter(r -> r.getStatus() == StatusReserva.CONCLUIDA
                        || (r.getStatus() == StatusReserva.CONFIRMADA && r.getCheckOut().isBefore(hoje))).toList();
                mesasAba = todasMesas.stream().filter(m -> m.getStatus() == StatusMesa.NAO_COMPARECEU
                        || (m.getStatus() == StatusMesa.CONFIRMADA && m.getData().isBefore(hoje))).toList();
            }
            case "canceladas" -> {
                estadias = todas.stream().filter(r -> r.getStatus() == StatusReserva.CANCELADA).toList();
                mesasAba = todasMesas.stream().filter(m -> m.getStatus() == StatusMesa.CANCELADA).toList();
            }
            default -> throw Util.validacao("A aba precisa ser proximas, passadas ou canceladas.");
        }
        // PENDENTE e EXPIRADA nunca aparecem nas abas.

        Map<UUID, String> nomes = cavernas.findAllById(estadias.stream().map(Reserva::getCavernaId).distinct().toList())
                .stream().collect(Collectors.toMap(Caverna::getId, Caverna::getNome));
        List<ReservaResumoRes> resEstadias = estadias.stream()
                .sorted(Comparator.comparing(Reserva::getCheckIn))
                .map(r -> new ReservaResumoRes(r.getCodigo(), r.getCavernaId(), nomes.get(r.getCavernaId()),
                        r.getCheckIn(), r.getCheckOut(), r.getStatus(), r.getTotal())).toList();
        List<MesaResumoRes> resMesas = mesasAba.stream()
                .sorted(Comparator.comparing(ReservaMesa::getData).thenComparing(ReservaMesa::getHorario))
                .map(m -> new MesaResumoRes(m.getCodigo(), m.getData(), m.getHorario(), m.getPessoas(), m.getStatus())).toList();
        return new MinhasReservasRes(resEstadias, resMesas);
    }

    // ---------- internos ----------

    private Usuario buscar(UUID id) { return usuarios.findById(id).orElseThrow(Util::naoAutenticado); }

    private PerfilRes toPerfil(Usuario u) {
        NivelFidelidade nivel = NivelFidelidade.de(movimentos.somaGanhos(u.getId()));
        return new PerfilRes(u.getId(), u.getNome(), u.getSobrenome(), u.getEmail(), u.getCelular(), u.getPais(),
                u.getTipoDocumento(), mascarar(u.getNumeroDocumento()), u.getDataNascimento(),
                u.isAceitaOfertas(), u.getSaldoOssos(), nivel.getNome(), "PT");
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
