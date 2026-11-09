package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.service.RelatoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Avaliações", description = "Avaliações das estadias: nota, comentário e fotos")
@RestController
@RequestMapping(SecurityConfig.API + "/arquivos")
@RequiredArgsConstructor
public class ArquivoController {

    private final RelatoService relatoService;

    @Operation(summary = "Foto de uma avaliação", description = "As URLs já vêm prontas em RelatoRes.fotos.")
    @GetMapping("/relatos/{arquivo}")
    public ResponseEntity<Resource> fotoRelato(@PathVariable String arquivo) {
        RelatoService.FotoArquivo f = relatoService.foto(arquivo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(f.recurso());
    }
}
