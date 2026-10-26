package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

/** Extras escolhidos. Preço unitário CONGELADO no momento da compra. */
@Entity
@Table(name = "reserva_extra")
@IdClass(ReservaExtra.Pk.class)
@Getter @Setter
public class ReservaExtra {
    @Id private UUID reservaId;
    @Id private String extraId;
    @Column(nullable = false) private int quantidade;
    @Column(nullable = false) private int precoUnitarioCentavos;
    @Column(nullable = false) private int subtotalCentavos;

    public static class Pk implements java.io.Serializable {
        private UUID reservaId;
        private String extraId;
        public Pk() {}
        public Pk(UUID reservaId, String extraId) { this.reservaId = reservaId; this.extraId = extraId; }
        @Override public boolean equals(Object o) {
            return o instanceof Pk p && Objects.equals(reservaId, p.reservaId) && Objects.equals(extraId, p.extraId);
        }
        @Override public int hashCode() { return Objects.hash(reservaId, extraId); }
    }
}
