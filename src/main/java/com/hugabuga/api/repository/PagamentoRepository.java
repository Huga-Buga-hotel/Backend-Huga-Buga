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

    /** Job: Pix pendente com validade vencida. */
    List<Pagamento> findByMetodoAndStatusAndPixExpiraEmBefore(MetodoPagamento metodo, StatusPagamento status,
                                                              OffsetDateTime limite);
}
