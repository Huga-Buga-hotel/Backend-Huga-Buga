package com.hugabuga.api.repository;

import com.hugabuga.api.model.RedefinicaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, UUID> {
    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);
}
