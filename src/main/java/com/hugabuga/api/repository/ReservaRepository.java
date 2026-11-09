package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ReservaRepository extends JpaRepository<Reserva, UUID>, JpaSpecificationExecutor<Reserva> {

    Optional<Reserva> findByCodigo(String codigo);

    /** Trava a linha (SELECT ... FOR UPDATE): serializa cliques duplos e ações concorrentes na mesma reserva. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.codigo = :codigo")
    Optional<Reserva> findParaAtualizarByCodigo(@Param("codigo") String codigo);

    List<Reserva> findByUsuarioId(UUID usuarioId);

    /** Job de expiração: PENDENTE vencidas. */
    List<Reserva> findByStatusAndExpiraEmBefore(StatusReserva status, OffsetDateTime limite);

    /** Reservas que seguram unidades da caverna em alguma noite do período [de, ate): calendário de disponibilidade. */
    @Query("""
            select r from Reserva r
            where r.cavernaId = :cavernaId
              and r.status in (com.hugabuga.api.model.StatusReserva.PENDENTE,
                               com.hugabuga.api.model.StatusReserva.AGUARDANDO_PAGAMENTO,
                               com.hugabuga.api.model.StatusReserva.CONFIRMADA)
              and r.checkIn < :ate and r.checkOut > :de
            """)
    List<Reserva> ativasNoPeriodo(@Param("cavernaId") UUID cavernaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Tocas ocupadas no hotel inteiro em uma noite (painel do admin). */
    @Query("""
            select r from Reserva r
            where r.status in (com.hugabuga.api.model.StatusReserva.PENDENTE,
                               com.hugabuga.api.model.StatusReserva.AGUARDANDO_PAGAMENTO,
                               com.hugabuga.api.model.StatusReserva.CONFIRMADA)
              and r.checkIn <= :noite and r.checkOut > :noite
            """)
    List<Reserva> ativasNaNoite(@Param("noite") LocalDate noite);

    // Consultas explícitas: por nome, "findByCheckIn..." seria lido como "check IN (...)".
    @Query("select r from Reserva r where r.checkIn = :data and r.status = :status order by r.codigo")
    List<Reserva> entradasDoDia(@Param("data") LocalDate data, @Param("status") StatusReserva status);

    @Query("select r from Reserva r where r.checkOut = :data and r.status = :status order by r.codigo")
    List<Reserva> saidasDoDia(@Param("data") LocalDate data, @Param("status") StatusReserva status);

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
