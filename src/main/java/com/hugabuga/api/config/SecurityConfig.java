package com.hugabuga.api.config;

import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.service.JwtService;
import com.hugabuga.api.service.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Sessão stateless, BCrypt, CORS para o front, CSRF desligado (API com token no header).
 *
 * Níveis de acesso:
 *  - livre:  qualquer um;
 *  - login:  token JWT válido (role VISITANTE ou ADMIN);
 *  - dono:   a rota é aberta aqui, mas o SERVICE confere se a reserva é do usuário logado ou se o pedido
 *            traz o e-mail do responsável (ReservaService.verificarDono). É o que permite reservar sem conta.
 *            ADMIN passa nessa conferência para qualquer reserva;
 *  - admin:  /admin/** exige role ADMIN.
 */
@Configuration
public class SecurityConfig {

    /** Prefixo das rotas. Os controllers mapeiam @RequestMapping("/api/v1/..."). */
    public static final String API = "/api/v1";

    @Value("${hugabuga.cors.origens:http://localhost:3000,http://localhost:5173}")
    private String[] origensPermitidas;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        // sem token em rota protegida: 401 NAO_AUTENTICADO
                        .authenticationEntryPoint((req, res, ex) -> RespostaErro.escrever(res, Util.naoAutenticado()))
                        .accessDeniedHandler((req, res, ex) -> RespostaErro.escrever(res, new ApiException(
                                HttpStatus.FORBIDDEN, "SEM_PERMISSAO", "Uga! Essa toca não é sua.",
                                "Você não tem permissão para isso. Nada foi alterado. Entre com a conta certa e tente de novo."))))
                .authorizeHttpRequests(a -> a
                        // --- livres: sistema ---
                        .requestMatchers("/error", API + "/health", API + "/docs", API + "/docs/**",
                                API + "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // --- Auth: cadastro, login e refresh livres; logout exige login ---
                        .requestMatchers(HttpMethod.POST, API + "/auth/cadastro", API + "/auth/login", API + "/auth/refresh",
                                API + "/auth/esqueci-senha", API + "/auth/redefinir-senha").permitAll()
                        .requestMatchers(API + "/auth/**").authenticated()

                        // --- equipe do hotel ---
                        .requestMatchers(API + "/admin/**").hasRole("ADMIN")

                        // --- exigem login (precisam vir ANTES das regras de /reservas/**) ---
                        .requestMatchers(API + "/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST, API + "/reservas/*/relato").authenticated()

                        // --- livres: leitura de catálogo, busca e reserva sem conta ---
                        .requestMatchers(HttpMethod.GET, API + "/home", API + "/cavernas/**", API + "/comodidades",
                                API + "/atividades/**", API + "/extras", API + "/arquivos/**").permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/reservas", API + "/alertas-vaga").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/reservas/buscar").permitAll()

                        // --- dono: abertas aqui, autorização por reserva dentro do service ---
                        .requestMatchers(API + "/reservas/**", API + "/pagamentos/**", API + "/restaurante/**").permitAll()

                        // qualquer rota não prevista fica protegida (nunca aberta por esquecimento)
                        .anyRequest().authenticated())
                // O filtro é criado aqui (e não é @Component) para não ser registrado duas vezes
                .addFilterBefore(new JwtFilter(jwtService, API), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origensPermitidas).map(String::trim).filter(s -> !s.isEmpty()).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Responsavel-Email"));
        c.setExposedHeaders(List.of("Content-Disposition")); // download do voucher.pdf
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(API + "/**", c);
        return source;
    }
}
