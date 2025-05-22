package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authProvider;
    private final BridgeTokenFilter bridgeTokenFilter;

    /**
     * <-- Aquí indicamos a Spring Security que IGNORE completamente
     *     estas rutas de WebSocket/SockJS, ni siquiera pasarán por el filter‐chain.
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
            .requestMatchers(
                new AntPathRequestMatcher("/ws/**"),
                new AntPathRequestMatcher("/ws-sockjs/**")
            );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e)
                    -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e)
                    -> res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
            )
            .authorizeHttpRequests(auth -> auth
                // 1) Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 2) Endpoints públicos de auth
                .requestMatchers(HttpMethod.POST,
                    "/auth/loginUser",
                    "/auth/loginCompany",
                    "/auth/loginBranch",
                    "/auth/refresh-token"
                ).permitAll()
                // 3) Registrar usuario
                .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                // 4) Otros públicos
                .requestMatchers(
                    "/error/**",
                    "/api/mercadopago/**",
                    "/api/webhooks/mercadopago",
                    "/actuator/health",
                    "/actuator/info",
                    "/api/branches"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/print/jobs/next").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/print/jobs/*/ack").permitAll()
                // 5) Rutas de /auth/** deben llevar JWT
                .requestMatchers("/auth/**").authenticated()
                // 6) Todo lo demás requiere Bridge‐Token o JWT
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authProvider)
            // Insertamos los filtros de JWT y Bridge‐Token
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
