package com.hugabuga.api.service;

import com.hugabuga.api.dto.CavernaDtos.RelatoRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.FotoRelatoRepository;
import com.hugabuga.api.repository.RelatoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Avaliações (relatos) das estadias: nota de 1 a 5, comentário e até 5 fotos de até 25 MB cada.
 * Só quem tem conta e ficou na caverna (reserva CONCLUIDA) avalia, uma vez por estadia.
 */
@Service
@RequiredArgsConstructor
public class RelatoService {

    static final String PASTA = "relatos";
    private static final int TEXTO_MAX = 2000;

    private final ReservaService reservaService;
    private final CavernaService cavernaService;
    private final RelatoRepository relatos;
    private final FotoRelatoRepository fotos;
    private final ArmazenamentoService armazenamento;

    @Value("${hugabuga.uploads.max-fotos-por-avaliacao:5}") private int maxFotos;

    @Transactional
    public RelatoRes registrar(String codigo, int nota, String texto, List<MultipartFile> arquivos, Solicitante s) {
        if (nota < 1 || nota > 5) throw Util.validacao("A nota vai de 1 a 5.");
        if (texto != null && texto.length() > TEXTO_MAX) throw Util.validacao("O comentário pode ter até " + TEXTO_MAX + " caracteres.");

        Reserva r = reservaService.carregarDonoParaEscrita(codigo, s);
        reservaService.exigirStatus(r, StatusReserva.CONCLUIDA);
        if (r.getUsuarioId() == null) throw Util.estadoInvalido("Só quem reservou com conta pode avaliar. Entre na sua conta.");
        if (!r.getUsuarioId().equals(s.usuarioId())) throw Util.semPermissao(); // nem o admin avalia por alguém
        if (relatos.existsByReservaId(r.getId())) throw Util.estadoInvalido("Esta estadia já foi avaliada.");

        // Confere TODAS as imagens antes de gravar qualquer coisa
        List<MultipartFile> enviados = arquivos == null ? List.of() : arquivos.stream().filter(f -> !f.isEmpty()).toList();
        if (enviados.size() > maxFotos) throw Util.validacao("Envie no máximo " + maxFotos + " fotos por avaliação.");
        List<ArmazenamentoService.Imagem> imagens = enviados.stream().map(armazenamento::validarImagem).toList();

        Relato rel = new Relato();
        rel.setCavernaId(r.getCavernaId());
        rel.setReservaId(r.getId());
        rel.setUsuarioId(r.getUsuarioId());
        rel.setNota(nota);
        rel.setTexto(texto == null || texto.isBlank() ? null : texto.trim());
        rel.setCriadoEm(Util.agora());
        rel = relatos.save(rel);

        List<String> gravados = new ArrayList<>();
        apagarSeDerRollback(gravados); // se a transação falhar, os arquivos não ficam órfãos no disco
        for (int i = 0; i < imagens.size(); i++) {
            ArmazenamentoService.Imagem img = imagens.get(i);
            String nome = armazenamento.salvar(PASTA, img);
            gravados.add(nome);
            FotoRelato f = new FotoRelato();
            f.setRelatoId(rel.getId());
            f.setArquivo(nome);
            f.setContentType(img.contentType());
            f.setTamanhoBytes(img.arquivo().getSize());
            f.setOrdem(i);
            fotos.save(f);
        }
        cavernaService.recalcularNota(r.getCavernaId());
        return cavernaService.toRelatos(List.of(rel)).getFirst();
    }

    /** Moderação (ADMIN): apaga a avaliação, as fotos e recalcula a nota da caverna. */
    @Transactional
    public void excluir(UUID relatoId) {
        Relato rel = relatos.findById(relatoId).orElseThrow(() -> Util.naoEncontrado("Avaliação"));
        List<String> arquivos = fotos.findByRelatoIdOrderByOrdem(relatoId).stream().map(FotoRelato::getArquivo).toList();
        relatos.delete(rel); // as linhas de foto_relato saem junto (ON DELETE CASCADE)
        relatos.flush();
        cavernaService.recalcularNota(rel.getCavernaId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { arquivos.forEach(a -> armazenamento.apagar(PASTA, a)); }
        });
    }

    /** Arquivo de uma foto, só se ela pertence a uma avaliação publicada. */
    @Transactional(readOnly = true)
    public FotoArquivo foto(String arquivo) {
        FotoRelato f = fotos.findByArquivo(arquivo).orElseThrow(() -> Util.naoEncontrado("Foto"));
        return new FotoArquivo(armazenamento.carregar(PASTA, f.getArquivo()), f.getContentType());
    }

    public record FotoArquivo(Resource recurso, String contentType) {}

    private void apagarSeDerRollback(List<String> gravados) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) gravados.forEach(a -> armazenamento.apagar(PASTA, a));
            }
        });
    }
}
