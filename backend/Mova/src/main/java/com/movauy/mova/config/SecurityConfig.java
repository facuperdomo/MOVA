package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
     * Excluye del filter chain tu webhook y el dispatcher de /error
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
            .requestMatchers("/api/webhooks/mercadopago")
            .requestMatchers("/error", "/error/**");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        http
            // 1) CSRF deshabilitado (porque usas tokens)
            .csrf(csrf -> csrf.disable())

            // 2) CORS aplicado sólo aquí
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 3) Manejo de errores personalizado (401 / 403)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
            )

            // 4) Reglas de acceso
            .authorizeHttpRequests(auth -> auth
                // 4.1) CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 4.2) Tu webhook (ya ignorado por WebSecurityCustomizer) 
                //      y el dispatcher de errores también (ignored)

                // 4.3) Endpoints públicos de autenticación
                .requestMatchers(HttpMethod.POST,
                    "/auth/loginUser",
                    "/auth/loginCompany",
                    "/auth/loginBranch",
                    "/auth/refresh-token",
                    "/auth/register"
                ).permitAll()

                // 4.4) Otros públicos
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/api/branches"
                ).permitAll()

                // 4.5) WebSocket/SockJS y creación de preference MP
                .requestMatchers(
                    "/ws/**",
                    "/ws-sockjs/**",
                    "/api/mercadopago/**"
                ).permitAll()

                // 4.6) `/auth/**` sí requiere JWT
                .requestMatchers("/auth/**").authenticated()

                // 4.7) El resto (API privadas) requieren JWT o Bridge‐Token
                .anyRequest().authenticated()
            )

            // 5) Stateless session
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 6) Tus proveedores / filtros
            .authenticationProvider(authProvider)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(bridgeTokenFilter, JwtAuthenticationFilter.class)
        ;

        return http.build();
    }

    /**
     * Configuración de CORS global
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
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
