package com.hugabuga.api.repository;

import com.hugabuga.api.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.*;
import java.util.*;

public interface CavernaRepository extends JpaRepository<Caverna, UUID> {

    // EntityGraph carrega as comodidades junto: evita N+1 na lista de cavernas.
    @EntityGraph(attributePaths = "comodidades")
    List<Caverna> findByAtivaTrue();

    @EntityGraph(attributePaths = "comodidades")
    Optional<Caverna> findBySlugAndAtivaTrue(String slug);

    @Override
    @EntityGraph(attributePaths = "comodidades")
    List<Caverna> findAllById(Iterable<UUID> ids);
}
