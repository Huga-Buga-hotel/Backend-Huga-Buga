package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface FotoCavernaRepository extends JpaRepository<FotoCaverna, UUID> {
    List<FotoCaverna> findByCavernaIdOrderByOrdem(UUID cavernaId);
    /** Uma consulta só para as capas de vários cards (a primeira por caverna é a de menor ordem). */
    List<FotoCaverna> findByCavernaIdInOrderByOrdem(Collection<UUID> cavernaIds);
}
