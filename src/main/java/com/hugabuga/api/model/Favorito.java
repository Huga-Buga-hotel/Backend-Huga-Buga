package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Coração nos cards. Chave composta usuarioId + cavernaId. */
@Entity
@Table(name = "favorito")
@IdClass(Favorito.Pk.class)
@Getter @Setter
public class Favorito {
    @Id private UUID usuarioId;
    @Id private UUID cavernaId;

    public Favorito() {}
    public Favorito(UUID usuarioId, UUID cavernaId) { this.usuarioId = usuarioId; this.cavernaId = cavernaId; }

    public static class Pk implements java.io.Serializable {
        private UUID usuarioId;
        private UUID cavernaId;
        public Pk() {}
        public Pk(UUID usuarioId, UUID cavernaId) { this.usuarioId = usuarioId; this.cavernaId = cavernaId; }
        @Override public boolean equals(Object o) {
            return o instanceof Pk p && Objects.equals(usuarioId, p.usuarioId) && Objects.equals(cavernaId, p.cavernaId);
        }
        @Override public int hashCode() { return Objects.hash(usuarioId, cavernaId); }
    }
}
