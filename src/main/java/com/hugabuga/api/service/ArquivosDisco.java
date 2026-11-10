package com.hugabuga.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Uploads no disco local ({@code hugabuga.uploads.dir}; no Docker, um volume). */
@Component
@ConditionalOnProperty(name = "hugabuga.uploads.tipo", havingValue = "disco", matchIfMissing = true)
public class ArquivosDisco implements Arquivos {

    private final Path raiz;

    public ArquivosDisco(@Value("${hugabuga.uploads.dir:./uploads}") String dir) {
        this.raiz = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public void gravar(String chave, MultipartFile arquivo, String contentType) throws IOException {
        Path destino = resolver(chave);
        Files.createDirectories(destino.getParent());
        arquivo.transferTo(destino);
    }

    @Override
    public Resource ler(String chave) {
        Path p = resolver(chave);
        return Files.isReadable(p) ? new PathResource(p) : null;
    }

    @Override
    public void apagar(String chave) throws IOException {
        Files.deleteIfExists(resolver(chave));
    }

    /** Segunda barreira contra path traversal, além da validação do nome. */
    private Path resolver(String chave) {
        Path p = raiz.resolve(chave).normalize();
        if (!p.startsWith(raiz)) throw new IllegalArgumentException("Chave fora da pasta de uploads");
        return p;
    }
}
