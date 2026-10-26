package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface MembroTriboRepository extends JpaRepository<MembroTribo, UUID> {
    List<MembroTribo> findByUsuarioId(UUID usuarioId);
    /** Garante que o membro pertence ao usuário logado. */
    Optional<MembroTribo> findByIdAndUsuarioId(UUID id, UUID usuarioId);
}
