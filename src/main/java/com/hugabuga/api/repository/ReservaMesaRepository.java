package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ReservaMesaRepository extends JpaRepository<ReservaMesa, UUID> {
    Optional<ReservaMesa> findByCodigo(String codigo);
    List<ReservaMesa> findByReservaId(UUID reservaId);
    List<ReservaMesa> findByUsuarioId(UUID usuarioId);

    /** Pessoas já reservadas (só CONFIRMADA) em uma data/horário. */
    default int somaPessoas(LocalDate data, LocalTime horario) {
        Long soma = somarPessoasConfirmadas(data, horario);
        return soma == null ? 0 : soma.intValue();
    }

    @Query("""
            select coalesce(sum(m.pessoas), 0) from ReservaMesa m
            where m.data = :data and m.horario = :horario
              and m.status = com.hugabuga.api.model.StatusMesa.CONFIRMADA
            """)
    Long somarPessoasConfirmadas(@Param("data") LocalDate data, @Param("horario") LocalTime horario);
}
