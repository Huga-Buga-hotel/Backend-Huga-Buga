package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface UnidadeCavernaRepository extends JpaRepository<UnidadeCaverna, UUID> {

    /**
     * Unidades livres no período. A data de saída do hóspede anterior NÃO bloqueia a entrada do próximo
     * (por isso as comparações são estritas: checkIn existente < saída nova E checkOut existente > entrada nova).
     */
    @Query("""
            select u from UnidadeCaverna u
            where u.cavernaId = :cavernaId and u.ativa = true
              and not exists (
                select 1 from Reserva r
                where r.unidadeId = u.id
                  and r.status in (com.hugabuga.api.model.StatusReserva.PENDENTE,
                                   com.hugabuga.api.model.StatusReserva.AGUARDANDO_PAGAMENTO,
                                   com.hugabuga.api.model.StatusReserva.CONFIRMADA)
                  and r.checkIn < :checkOut and r.checkOut > :checkIn)
            order by u.codigo
            """)
    List<UnidadeCaverna> unidadesLivres(@Param("cavernaId") UUID cavernaId,
                                        @Param("checkIn") LocalDate checkIn,
                                        @Param("checkOut") LocalDate checkOut);

    /**
     * Escolhe UMA unidade livre travando a linha (FOR UPDATE SKIP LOCKED): duas tribos simultâneas
     * nunca recebem a mesma toca. Precisa rodar dentro de transação. A restrição de exclusão do
     * banco continua sendo a última barreira.
     */
    @Query(value = """
            SELECT u.* FROM unidade_caverna u
            WHERE u.caverna_id = :cavernaId AND u.ativa = true
              AND NOT EXISTS (
                SELECT 1 FROM reserva r
                WHERE r.unidade_id = u.id
                  AND r.status IN ('PENDENTE', 'AGUARDANDO_PAGAMENTO', 'CONFIRMADA')
                  AND r.check_in < :checkOut AND r.check_out > :checkIn)
            ORDER BY u.codigo
            LIMIT 1
            FOR UPDATE OF u SKIP LOCKED
            """, nativeQuery = true)
    Optional<UnidadeCaverna> primeiraLivreComBloqueio(@Param("cavernaId") UUID cavernaId,
                                                      @Param("checkIn") LocalDate checkIn,
                                                      @Param("checkOut") LocalDate checkOut);
}
