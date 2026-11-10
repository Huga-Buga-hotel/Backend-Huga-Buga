package com.hugabuga.api.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Uploads num bucket S3 privado. O bucket NÃO é público: as fotos continuam saindo por
 * GET /api/v1/arquivos/relatos/{arquivo}, que confere se a foto pertence a uma avaliação publicada.
 *
 * Credenciais: cadeia padrão da AWS (na EC2, a IAM role da instância; nada de chave no .env).
 */
@Component
@ConditionalOnProperty(name = "hugabuga.uploads.tipo", havingValue = "s3")
public class ArquivosS3 implements Arquivos {

    private final S3Client s3;
    private final String bucket;
    private final String prefixo;

    public ArquivosS3(@Value("${hugabuga.uploads.s3.bucket}") String bucket,
                      @Value("${hugabuga.uploads.s3.regiao:us-east-1}") String regiao,
                      @Value("${hugabuga.uploads.s3.prefixo:uploads/}") String prefixo) {
        this.s3 = S3Client.builder().region(Region.of(regiao)).build();
        this.bucket = bucket;
        this.prefixo = prefixo;
    }

    @Override
    public void gravar(String chave, MultipartFile arquivo, String contentType) throws IOException {
        try (InputStream in = arquivo.getInputStream()) {
            s3.putObject(r -> r.bucket(bucket).key(prefixo + chave).contentType(contentType),
                    RequestBody.fromInputStream(in, arquivo.getSize()));
        } catch (RuntimeException e) {
            throw new IOException("Falha ao enviar " + chave + " para o S3", e);
        }
    }

    @Override
    public Resource ler(String chave) {
        try {
            return new InputStreamResource(s3.getObject(r -> r.bucket(bucket).key(prefixo + chave)));
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void apagar(String chave) throws IOException {
        try {
            s3.deleteObject(r -> r.bucket(bucket).key(prefixo + chave));
        } catch (RuntimeException e) {
            throw new IOException("Falha ao apagar " + chave + " do S3", e);
        }
    }

    @PreDestroy
    void fechar() {
        s3.close();
    }
}
