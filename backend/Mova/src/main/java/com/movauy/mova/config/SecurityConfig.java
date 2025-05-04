package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1) Aseguramos que Spring use nuestro CorsConfigurationSource
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                // 2) Manejamos los errores de auth/denegación
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx)
                        -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, authEx.getMessage()))
                .accessDeniedHandler((req, res, deniedEx)
                        -> res.sendError(HttpServletResponse.SC_FORBIDDEN, deniedEx.getMessage()))
                )
                // 3) Configuramos qué URLs se permiten sin token...
                .authorizeHttpRequests(auth -> auth
                // Dejar pasar TODOS los OPTIONS (preflight) a cualquier ruta
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Endpoints públicos
                .requestMatchers(
                        "/auth/**",
                        "/error/**",
                        "/api/mercadopago/**",
                        "/api/webhooks/mercadopago",
                        "/ws/**"
                ).permitAll()
                .requestMatchers("/api/print/**").permitAll()
                // El resto requiere JWT
                .anyRequest().authenticated()
                )
                // 4) Stateless, usamos nuestro AuthenticationProvider y JWT filter
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Nuestro bean CORS global, se usará gracias a http.cors(...)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        // Orígenes permitidos
        cfg.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://movauy.top",
                "https://movauy.top:8443"
        ));

        // Métodos permitidos, ahora incluyendo PATCH
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Cabeceras permitidas
        cfg.setAllowedHeaders(List.of("*"));

        // Necesario si envías Authorization
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

}
