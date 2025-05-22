package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authProvider;
    private final BridgeTokenFilter bridgeTokenFilter;

    /**
     * 1) Ignorar por completo estos endpoints
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
            .requestMatchers(
                new AntPathRequestMatcher("/ws/**"),
                new AntPathRequestMatcher("/ws-sockjs/**"),
                new AntPathRequestMatcher("/api/mercadopago/**")
            );
    }

    /**
     * 2) Filter‐chain principal
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
            )
            .authorizeHttpRequests(auth -> auth
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Public Auth endpoints
                .requestMatchers(HttpMethod.POST,
                    "/auth/loginUser",
                    "/auth/loginCompany",
                    "/auth/loginBranch",
                    "/auth/refresh-token",
                    "/auth/register"
                ).permitAll()

                // Otros públicos
                .requestMatchers(
                    "/error/**",
                    "/api/webhooks/mercadopago",
                    "/actuator/health",
                    "/actuator/info",
                    "/api/branches"
                ).permitAll()

                // Asegurarnos de no bloquear tampoco los WS/SockJS en HttpSecurity
                .requestMatchers("/ws/**", "/ws-sockjs/**", "/api/mercadopago/**").permitAll()

                // /auth/** requiere JWT
                .requestMatchers("/auth/**").authenticated()

                // Todo lo demás: JWT ó Bridge‐Token
                .anyRequest().authenticated()
            )
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authProvider)
            // filtros de seguridad
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(bridgeTokenFilter, JwtAuthenticationFilter.class)
        ;

        return http.build();
    }

    /**
     * Configuración de CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "https://movauy.top",
            "https://www.movauy.top",
            "https://movauy.top:8443"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
