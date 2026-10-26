package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface ReservaExtraRepository extends JpaRepository<ReservaExtra, ReservaExtra.Pk> {
    List<ReservaExtra> findByReservaId(UUID reservaId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ReservaExtra e where e.reservaId = :reservaId")
    void deleteByReservaId(@Param("reservaId") UUID reservaId);
}
