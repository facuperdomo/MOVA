// src/main/java/com/movauy/mova/config/SecurityConfig.java
package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BridgeTokenFilter bridgeTokenFilter;
    private final AuthenticationProvider authProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1) Nada de CSRF
            .csrf(csrf -> csrf.disable())

            // 2) CORS general
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3) 401/403 personalizados
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
            )

            // 4) Reglas de acceso
            .authorizeHttpRequests(auth -> auth
                // 4.1) Preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 4.2) Login / register
                .requestMatchers(HttpMethod.POST,
                    "/auth/loginUser",
                    "/auth/loginCompany",
                    "/auth/loginBranch",
                    "/auth/refresh-token",
                    "/auth/register"
                ).permitAll()

                // 4.3) Otros públicos
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/api/branches",
                    "/ws/**",
                    "/ws-sockjs/**",
                    "/api/mercadopago/**"
                ).permitAll()

                // 4.4) /auth/** sí requiere JWT
                .requestMatchers("/auth/**").authenticated()

                // 4.5) Todo lo demás: JWT o Bridge‐Token
                .anyRequest().authenticated()
            )

            // 5) Stateless
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 6) Filtros y provider
            .authenticationProvider(authProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(bridgeTokenFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "https://movauy.top",
            "https://www.movauy.top",
            "https://movauy.top:8443"
        ));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
