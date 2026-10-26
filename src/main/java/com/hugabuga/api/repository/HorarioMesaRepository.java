package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface HorarioMesaRepository extends JpaRepository<HorarioMesa, UUID> {
    List<HorarioMesa> findAllByOrderByHorario();

    /** Trava a linha do horário: reservas simultâneas não estouram a capacidade. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HorarioMesa h where h.horario = :horario")
    Optional<HorarioMesa> travarPorHorario(@Param("horario") LocalTime horario);
}
