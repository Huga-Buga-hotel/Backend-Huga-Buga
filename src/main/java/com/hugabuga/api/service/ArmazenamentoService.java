package com.hugabuga.api.service;

import com.hugabuga.api.erro.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Grava as imagens enviadas no disco local ({@code hugabuga.uploads.dir}; no Docker, um volume).
 *
 * Segurança: o tipo é decidido pelos primeiros bytes do arquivo (não pelo nome nem pelo Content-Type que o
 * cliente declara), o nome no disco é sempre gerado aqui (UUID) e a leitura só aceita nomes nesse formato,
 * o que impede path traversal ("../../etc/passwd").
 */
@Slf4j
@Service
public class ArmazenamentoService {

    /** Imagem já conferida, pronta para gravar. */
    public record Imagem(MultipartFile arquivo, String contentType, String extensao) {}

    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.(jpg|png|webp)");

    private final Path raiz;

    public ArmazenamentoService(@Value("${hugabuga.uploads.dir:./uploads}") String dir) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
    }

    /** Aceita só JPG, PNG e WEBP, reconhecidos pela assinatura do arquivo. */
    public Imagem validarImagem(MultipartFile f) {
        byte[] cabecalho;
        try (InputStream in = f.getInputStream()) {
            cabecalho = in.readNBytes(12);
        } catch (IOException e) {
            throw Util.validacao("Não conseguimos ler a imagem " + nomeSeguro(f) + ".");
        }
        if (comeca(cabecalho, 0xFF, 0xD8, 0xFF)) return new Imagem(f, "image/jpeg", "jpg");
        if (comeca(cabecalho, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return new Imagem(f, "image/png", "png");
        if (cabecalho.length >= 12 && comeca(cabecalho, 'R', 'I', 'F', 'F')
                && cabecalho[8] == 'W' && cabecalho[9] == 'E' && cabecalho[10] == 'B' && cabecalho[11] == 'P') {
            return new Imagem(f, "image/webp", "webp");
        }
        throw Util.validacao("O arquivo " + nomeSeguro(f) + " não é uma imagem JPG, PNG ou WEBP.");
    }

    /** Grava em {@code <raiz>/<pasta>/<uuid>.<ext>} e devolve o nome gerado. */
    public String salvar(String pasta, Imagem img) {
        String nome = UUID.randomUUID() + "." + img.extensao();
        try {
            Path dir = Files.createDirectories(raiz.resolve(pasta));
            img.arquivo().transferTo(dir.resolve(nome));
            return nome;
        } catch (IOException e) {
            log.error("Falha ao gravar upload em {}", raiz.resolve(pasta), e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INDISPONIVEL", "Ugh! Não deu pra guardar as fotos.",
                    "As fotos não puderam ser salvas agora e nada foi publicado. Tente de novo em instantes.");
        }
    }

    public Resource carregar(String pasta, String nome) {
        Path arquivo = caminho(pasta, nome);
        if (arquivo == null || !Files.isReadable(arquivo)) throw Util.naoEncontrado("Arquivo");
        return new PathResource(arquivo);
    }

    /** Remove sem lançar erro (usado na limpeza depois de falhas e exclusões). */
    public void apagar(String pasta, String nome) {
        Path arquivo = caminho(pasta, nome);
        if (arquivo == null) return;
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", arquivo, e);
        }
    }

    private Path caminho(String pasta, String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) return null;
        Path p = raiz.resolve(pasta).resolve(nome).normalize();
        return p.startsWith(raiz) ? p : null;
    }

    private static boolean comeca(byte[] b, int... assinatura) {
        if (b.length < assinatura.length) return false;
        for (int i = 0; i < assinatura.length; i++) if ((b[i] & 0xFF) != assinatura[i]) return false;
        return true;
    }

    /** Nome original só para mensagens, sem caracteres de controle. */
    private static String nomeSeguro(MultipartFile f) {
        String n = f.getOriginalFilename() == null ? "enviado" : f.getOriginalFilename();
        n = n.replaceAll("[\\p{Cntrl}\"\\\\]", "");
        return "\"" + (n.length() > 60 ? n.substring(0, 60) + "..." : n) + "\"";
    }
}
