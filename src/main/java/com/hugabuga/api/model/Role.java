package com.hugabuga.api.model;

/** Papel do usuário. Vai no token JWT e vira a authority ROLE_VISITANTE / ROLE_ADMIN. */
public enum Role {
    /** Cliente do hotel: reserva, avalia, gerencia a própria conta. */
    VISITANTE,
    /** Equipe do hotel: acesso às rotas /admin e a qualquer reserva. */
    ADMIN
}
