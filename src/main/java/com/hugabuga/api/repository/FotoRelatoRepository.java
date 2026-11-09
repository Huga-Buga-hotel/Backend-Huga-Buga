package com.hugabuga.api.repository;

import com.hugabuga.api.model.FotoRelato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoRelatoRepository extends JpaRepository<FotoRelato, UUID> {
    List<FotoRelato> findByRelatoIdOrderByOrdem(UUID relatoId);
    /** Uma consulta só para as fotos de uma página de avaliações. */
    List<FotoRelato> findByRelatoIdInOrderByOrdem(Collection<UUID> relatoIds);
    Optional<FotoRelato> findByArquivo(String arquivo);
}
