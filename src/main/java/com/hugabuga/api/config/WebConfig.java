package com.hugabuga.api.config;

import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.UUID;

/**
 * Permite declarar um parâmetro {@link Solicitante} em qualquer método de controller.
 * Ele junta as duas formas de ser "dono" de uma reserva:
 *  - usuário logado (UUID do token JWT), e/ou
 *  - e-mail do responsável, enviado no header X-Responsavel-Email (reserva feita sem conta).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String HEADER_EMAIL = "X-Responsavel-Email";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new SolicitanteResolver());
    }

    static class SolicitanteResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Solicitante.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            UUID usuarioId = (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID u) ? u : null;
            String email = webRequest.getHeader(HEADER_EMAIL);
            if (email != null && email.isBlank()) email = null;
            return new Solicitante(usuarioId, email);
        }
    }
}
