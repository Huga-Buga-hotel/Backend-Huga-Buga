package com.hugabuga.api.repository;

import com.hugabuga.api.model.ComboBanquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboBanqueteRepository extends JpaRepository<ComboBanquete, String> {
    List<ComboBanquete> findByAtivoTrueOrderByPrecoAdultoCentavos();
}
