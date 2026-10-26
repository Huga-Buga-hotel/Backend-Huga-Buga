package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Status da API. Faz uma consulta simples ao banco: se ele estiver fora do ar, a exceção sobe e o
 * GlobalExceptionHandler responde 503 INDISPONIVEL (é o que alimenta a tela de erro do front).
 */
@RestController
@RequestMapping(SecurityConfig.API + "/health")
public class HealthController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping
    public Map<String, String> health() {
        em.createNativeQuery("select 1").getSingleResult();
        return Map.of("status", "UP");
    }
}
