package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    Optional<Reserva> findByCodigo(String codigo);

    /** Trava a linha (SELECT ... FOR UPDATE): serializa cliques duplos e ações concorrentes na mesma reserva. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.codigo = :codigo")
    Optional<Reserva> findParaAtualizarByCodigo(@Param("codigo") String codigo);

    List<Reserva> findByUsuarioId(UUID usuarioId);

    /** Job de expiração: PENDENTE vencidas. */
    List<Reserva> findByStatusAndExpiraEmBefore(StatusReserva status, OffsetDateTime limite);

    /** Job de conclusão: CONFIRMADA com check-out já passado. */
    List<Reserva> findByStatusAndCheckOutBefore(StatusReserva status, LocalDate limite);

    /** Outra reserva ativa na mesma unidade cruzando as datas? (ignora a própria reserva ao alterar datas). */
    default boolean existeConflito(UUID unidadeId, LocalDate checkIn, LocalDate checkOut, UUID ignorarReservaId) {
        return contarConflitos(unidadeId, checkIn, checkOut, ignorarReservaId) > 0;
    }

    @Query("""
            select count(r) from Reserva r
            where r.unidadeId = :unidadeId and r.id <> :ignorar
              and r.status in (com.hugabuga.api.model.StatusReserva.PENDENTE,
                               com.hugabuga.api.model.StatusReserva.AGUARDANDO_PAGAMENTO,
                               com.hugabuga.api.model.StatusReserva.CONFIRMADA)
              and r.checkIn < :checkOut and r.checkOut > :checkIn
            """)
    long contarConflitos(@Param("unidadeId") UUID unidadeId,
                         @Param("checkIn") LocalDate checkIn,
                         @Param("checkOut") LocalDate checkOut,
                         @Param("ignorar") UUID ignorar);
}
