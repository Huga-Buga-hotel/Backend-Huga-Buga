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

    Optional<HorarioMesa> findByHorario(LocalTime horario);

    /**
     * Trava TODOS os horários (SELECT ... FOR UPDATE). A capacidade é do salão e mesas de horários vizinhos
     * se sobrepõem, então duas reservas simultâneas em qualquer horário precisam ser serializadas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HorarioMesa h order by h.horario")
    List<HorarioMesa> travarTodos();
}
