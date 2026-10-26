package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface MovimentoOssosRepository extends JpaRepository<MovimentoOssos, UUID> {
    List<MovimentoOssos> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

    /**
     * Total ACUMULADO de ossos ganhos (só GANHO_ESTADIA). O nível é calculado sobre isso,
     * e não sobre o saldo, para nunca cair. Devoluções (AJUSTE) não contam.
     */
    @Query("""
            select coalesce(sum(m.quantidade), 0) from MovimentoOssos m
            where m.usuarioId = :usuarioId and m.motivo = com.hugabuga.api.model.MotivoOssos.GANHO_ESTADIA
            """)
    long somaGanhos(@Param("usuarioId") UUID usuarioId);
}
