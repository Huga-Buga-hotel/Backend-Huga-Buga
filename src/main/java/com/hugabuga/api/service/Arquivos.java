package com.hugabuga.api.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Onde os uploads ficam guardados. A chave já chega validada pelo {@link ArmazenamentoService}
 * (formato {@code <pasta>/<uuid>.<ext>}).
 *  - {@link ArquivosDisco}: pasta local (desenvolvimento / Docker com volume). Padrão.
 *  - {@link ArquivosS3}: bucket S3 privado (produção na AWS), com {@code hugabuga.uploads.tipo=s3}.
 */
public interface Arquivos {

    void gravar(String chave, MultipartFile arquivo, String contentType) throws IOException;

    /** O arquivo, ou null se não existir. */
    Resource ler(String chave);

    void apagar(String chave) throws IOException;
}
