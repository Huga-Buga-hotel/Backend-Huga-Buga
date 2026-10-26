package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface FavoritoRepository extends JpaRepository<Favorito, Favorito.Pk> {
    List<Favorito> findByUsuarioId(UUID usuarioId);
    boolean existsByUsuarioIdAndCavernaId(UUID usuarioId, UUID cavernaId);

    @Modifying
    @Query("delete from Favorito f where f.usuarioId = :usuarioId and f.cavernaId = :cavernaId")
    void deleteByUsuarioIdAndCavernaId(@Param("usuarioId") UUID usuarioId, @Param("cavernaId") UUID cavernaId);
}
