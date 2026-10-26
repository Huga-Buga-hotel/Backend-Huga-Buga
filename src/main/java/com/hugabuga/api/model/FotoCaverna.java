package com.hugabuga.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "foto_caverna")
@Getter @Setter
public class FotoCaverna {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID cavernaId;
    @Column(nullable = false) private String url;
    private String legenda;
    private int ordem;
}
