package com.hugabuga.api.config;

import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentação OpenAPI. Swagger UI em /api/v1/docs; JSON em /v3/api-docs.
 *
 * Dois jeitos de se identificar (botão "Authorize"):
 *  - bearerAuth: o accessToken do login (role VISITANTE ou ADMIN);
 *  - responsavelEmail: header X-Responsavel-Email, para quem reservou sem conta.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    public static final String EMAIL = "responsavelEmail";

    static {
        // O Solicitante é montado pelo WebConfig a partir do token e do header: não é parâmetro da requisição.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Solicitante.class);
    }

    @Bean
    public OpenAPI hugaBugaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Huga Buga Hotel API")
                        .version("v1")
                        .description("""
                                API do Huga Buga Hotel (projeto de conclusão de curso): cavernas (suítes) e \
                                disponibilidade, checkout com extras, combo quarto + Banquete, cupom e pagamento \
                                simulado, restaurante O Banquete (mesa de hóspede ou de visitante de fora), \
                                atividades, avaliações com fotos e o painel da equipe (role ADMIN).

                                **Valores em centavos** (52000 = R$ 520,00). Datas em ISO (2026-12-10), fuso America/Sao_Paulo.

                                **Acesso:** rotas de catálogo são livres; `/me/**` exige login; rotas de uma reserva \
                                exigem ser o dono (token da conta que reservou **ou** header `X-Responsavel-Email`); \
                                `/admin/**` exige role ADMIN, que também pode agir em qualquer reserva.

                                **Erros** seguem sempre o mesmo formato: `status`, `codigo`, `titulo`, `mensagem`, \
                                `ref` e, quando houver, `campos` e `dados`.

                                Usuários de teste: `marina.souza@example.com` / `Uga!Buga2026` (VISITANTE) e \
                                `admin@hugabuga.example` / `Admin!Buga2026` (ADMIN)."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("accessToken devolvido por /auth/login (vale 15 min; renove em /auth/refresh)."))
                        .addSecuritySchemes(EMAIL, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(WebConfig.HEADER_EMAIL)
                                .description("E-mail do responsável, para quem reservou sem conta.")));
    }
}
