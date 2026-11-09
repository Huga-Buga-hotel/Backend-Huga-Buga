package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    List<Pagamento> findByReservaId(UUID reservaId);
    /** Histórico de pagamentos da conta. */
    List<Pagamento> findByReservaIdInOrderByCriadoEmDesc(Collection<UUID> reservaIds);
    /** Receita do painel do admin: pagamentos aprovados no período [de, ate). */
    @Query("""
            select coalesce(sum(p.valorCentavos), 0) from Pagamento p
            where p.status = com.hugabuga.api.model.StatusPagamento.APROVADO
              and p.aprovadoEm >= :de and p.aprovadoEm < :ate
            """)
    long somaAprovados(@Param("de") OffsetDateTime de, @Param("ate") OffsetDateTime ate);

    /** Job: Pix pendente com validade vencida. */
    List<Pagamento> findByMetodoAndStatusAndPixExpiraEmBefore(MetodoPagamento metodo, StatusPagamento status,
                                                              OffsetDateTime limite);
}
