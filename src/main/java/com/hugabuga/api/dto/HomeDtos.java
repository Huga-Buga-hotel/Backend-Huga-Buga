package com.hugabuga.api.dto;

import com.hugabuga.api.dto.AtividadeDtos.AtividadeRes;
import com.hugabuga.api.dto.CavernaDtos.CavernaCardRes;
import com.hugabuga.api.dto.CavernaDtos.RelatoRes;
import com.hugabuga.api.dto.RestauranteDtos.RestauranteRes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public final class HomeDtos {
    private HomeDtos() {}

    @Schema(description = "Tudo o que a home precisa em uma chamada: cavernas em destaque, aventuras, O Banquete e depoimentos.")
    public record HomeRes(List<CavernaCardRes> cavernas, List<AtividadeRes> aventuras, RestauranteRes banquete,
                          List<RelatoRes> depoimentos, int totalCavernas, int totalTocas) {}
}
