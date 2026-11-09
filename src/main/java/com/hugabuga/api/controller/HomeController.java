package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.HomeDtos.HomeRes;
import com.hugabuga.api.service.AtividadeService;
import com.hugabuga.api.service.CavernaService;
import com.hugabuga.api.service.RestauranteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "Dados da página inicial em uma chamada")
@RestController
@RequestMapping(SecurityConfig.API + "/home")
@RequiredArgsConstructor
public class HomeController {

    private final CavernaService cavernaService;
    private final AtividadeService atividadeService;
    private final RestauranteService restauranteService;

    @Operation(summary = "Página inicial", description = """
            "Nossas cavernas" (3 mais bem avaliadas), "Aventuras da tribo" (4), o card do Banquete e os últimos
            depoimentos. A busca do topo usa GET /cavernas.""")
    @GetMapping
    public HomeRes home() {
        var filtros = cavernaService.filtros();
        int totalCavernas = (int) filtros.categorias().stream().mapToLong(c -> c.quantidade()).sum();
        return new HomeRes(cavernaService.destaques(3), atividadeService.destaques(4), restauranteService.visaoGeral(),
                cavernaService.depoimentos(), totalCavernas, cavernaService.totalTocas());
    }
}
