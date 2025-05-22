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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final BridgeTokenFilter bridgeTokenFilter;
    private final AuthenticationProvider authProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
        http
                // 1) Stateles & CSRF off
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 2) CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 3) Errores 401/403 custom
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e)
                        -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e)
                        -> res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
                )
                // 4) Autorizar rutas
                .authorizeHttpRequests(auth -> auth
                // 4.1) Preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 4.2) Webhook de MercadoPago (sin JWT)
                .requestMatchers(
                        "/api/webhooks/mercadopago",
                        "/api/webhooks/mercadopago/**"
                ).permitAll()
                // 4.3) Dispatcher de errores (Spring ErrorController)
                .requestMatchers(
                        "/error",
                        "/error/**"
                ).permitAll()
                // 4.4) Login / register públicos
                .requestMatchers(HttpMethod.POST,
                        "/auth/loginUser",
                        "/auth/loginCompany",
                        "/auth/loginBranch",
                        "/auth/refresh-token",
                        "/auth/register"
                ).permitAll()
                // 4.5) Otros públicos
                .requestMatchers(
                        "/actuator/health",
                        "/actuator/info",
                        "/api/branches",
                        "/ws/**",
                        "/ws-sockjs/**",
                        "/api/mercadopago/**" // create-preference
                ).permitAll()
                // 4.6) /auth/** sí requiere JWT
                .requestMatchers("/auth/**").authenticated()
                // 4.7) Resto: JWT o Bridge‐Token
                .anyRequest().authenticated()
                )
                // 5) Añadir filtros de JWT / Bridge-Token
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
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
