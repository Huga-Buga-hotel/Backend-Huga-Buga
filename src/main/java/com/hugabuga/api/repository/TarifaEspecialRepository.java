package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface TarifaEspecialRepository extends JpaRepository<TarifaEspecial, UUID> {
    /** Between é inclusivo nas duas pontas. */
    List<TarifaEspecial> findByCavernaIdAndDataBetween(UUID cavernaId, LocalDate de, LocalDate ate);
}
