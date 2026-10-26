package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface SessaoAtividadeRepository extends JpaRepository<SessaoAtividade, UUID> {

    List<SessaoAtividade> findByAtividadeIdAndInicioBetweenOrderByInicio(UUID atividadeId,
                                                                         OffsetDateTime de, OffsetDateTime ate);

    /**
     * Atômico: só ocupa se ainda couber. Retorna 0 linhas se esgotou (ninguém vende a mesma vaga duas vezes).
     * clearAutomatically: depois do UPDATE, uma nova leitura traz o valor real do banco.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SessaoAtividade s set s.vagasOcupadas = s.vagasOcupadas + :qtd
            where s.id = :id and s.vagasOcupadas + :qtd <= s.vagasTotais
            """)
    int ocuparVagas(@Param("id") UUID id, @Param("qtd") int qtd);

    /** Devolve vagas sem nunca passar de zero. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update SessaoAtividade s set s.vagasOcupadas = s.vagasOcupadas - :qtd
            where s.id = :id and s.vagasOcupadas - :qtd >= 0
            """)
    int liberarVagas(@Param("id") UUID id, @Param("qtd") int qtd);
}
