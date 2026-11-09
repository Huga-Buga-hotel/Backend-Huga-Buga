package com.hugabuga.api;

import com.hugabuga.api.dto.CavernaDtos.RelatoRes;
import com.hugabuga.api.dto.ReservaDtos.ReservaRes;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.model.Reserva;
import com.hugabuga.api.model.StatusReserva;
import com.hugabuga.api.repository.RelatoRepository;
import com.hugabuga.api.service.RelatoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Avaliação da estadia: nota, comentário e fotos. */
class RelatoTest extends IntegrationTestBase {

    @Autowired RelatoService relatoService;
    @Autowired RelatoRepository relatoRepository;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

    private MultipartFile foto(String nome, byte[] conteudo) {
        return new MockMultipartFile("fotos", nome, "image/png", conteudo);
    }

    /** Reserva da Marina já encerrada (CONCLUIDA). */
    private String estadiaConcluida() {
        Solicitante s = marina();
        ReservaRes r = criarPadrao(s);
        pagamentoService.pagar(r.codigo(), naChegada(), s);
        Reserva e = reserva(r.codigo());
        e.setCheckIn(dias(-5));
        e.setCheckOut(dias(-2));
        reservaRepository.saveAndFlush(e);
        reservaService.concluirEstadias();
        assertEquals(StatusReserva.CONCLUIDA, reserva(r.codigo()).getStatus());
        return r.codigo();
    }

    @Test
    void avaliaComNotaComentarioEFotos() {
        String codigo = estadiaConcluida();

        RelatoRes rel = relatoService.registrar(codigo, 5, "  Melhor fogueira da vida!  ",
                List.of(foto("a.png", PNG), foto("b.jpg", JPG)), marina());

        assertEquals(5, rel.nota());
        assertEquals("Melhor fogueira da vida!", rel.texto());
        assertEquals("Marina S.", rel.autor());
        assertEquals(2, rel.fotos().size());
        assertTrue(rel.fotos().getFirst().startsWith("/api/v1/arquivos/relatos/"));
        assertTrue(rel.fotos().getFirst().endsWith(".png"));
        assertTrue(rel.fotos().get(1).endsWith(".jpg"), "o tipo vem dos bytes, não da extensão enviada");

        String arquivo = rel.fotos().getFirst().substring(rel.fotos().getFirst().lastIndexOf('/') + 1);
        RelatoService.FotoArquivo f = relatoService.foto(arquivo);
        assertEquals("image/png", f.contentType());
        assertTrue(f.recurso().exists());

        var caverna = cavernaRepository.findById(cavernaId(TOCA)).orElseThrow();
        assertEquals(1, caverna.getTotalRelatos(), "a nota da caverna é recalculada");
    }

    @Test
    void arquivoQueNaoEhImagemEhRecusadoENadaEhSalvo() {
        String codigo = estadiaConcluida();
        MultipartFile texto = new MockMultipartFile("fotos", "foto.png", "image/png", "não sou imagem".getBytes());
        assertErro(erro(() -> relatoService.registrar(codigo, 4, null, List.of(texto), marina())),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertFalse(relatoRepository.existsByReservaId(reserva(codigo).getId()));
    }

    @Test
    void noMaximo5Fotos() {
        String codigo = estadiaConcluida();
        List<MultipartFile> seis = Collections.nCopies(6, foto("a.png", PNG));
        assertErro(erro(() -> relatoService.registrar(codigo, 4, null, seis, marina())), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void soDepoisDaEstadiaEUmaVezSo() {
        ReservaRes ativa = criarPadrao(marina());
        assertErro(erro(() -> relatoService.registrar(ativa.codigo(), 5, null, null, marina())),
                HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");

        String codigo = estadiaConcluida();
        relatoService.registrar(codigo, 5, null, null, marina());
        assertErro(erro(() -> relatoService.registrar(codigo, 4, null, null, marina())),
                HttpStatus.UNPROCESSABLE_ENTITY, "ESTADO_INVALIDO");
    }

    @Test
    void notaForaDe1A5EhRecusada() {
        String codigo = estadiaConcluida();
        assertErro(erro(() -> relatoService.registrar(codigo, 6, null, null, marina())), HttpStatus.BAD_REQUEST, "VALIDACAO");
        assertErro(erro(() -> relatoService.registrar(codigo, 0, null, null, marina())), HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void adminNaoAvaliaPeloHospedeMasPodeModerar() {
        String codigo = estadiaConcluida();
        assertErro(erro(() -> relatoService.registrar(codigo, 1, null, null, admin())), HttpStatus.FORBIDDEN, "SEM_PERMISSAO");

        RelatoRes rel = relatoService.registrar(codigo, 2, "ruim", List.of(foto("a.png", PNG)), marina());
        relatoService.excluir(rel.id());

        assertFalse(relatoRepository.existsById(rel.id()));
        assertEquals(0, cavernaRepository.findById(cavernaId(TOCA)).orElseThrow().getTotalRelatos());
    }
}
