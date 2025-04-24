package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        return http
                // habilitamos CORS con nuestra configuración personalizada
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    System.out.println("AuthenticationEntryPoint invocado: " + authEx.getMessage());
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, authEx.getMessage());
                })
                .accessDeniedHandler((req, res, deniedEx) -> {
                    System.out.println("AccessDeniedHandler invocado: " + deniedEx.getMessage());
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, deniedEx.getMessage());
                })
                )
                .authorizeHttpRequests(auth -> auth
                // endpoints públicos
                .requestMatchers(
                        "/auth/**",
                        "/error/**",
                        "/api/mercadopago/**",
                        "/api/webhooks/mercadopago",
                        "/ws/**"
                ).permitAll()
                .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // en lugar de "*", los orígenes explícitos que usarán tu frontend o ngrok:
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://movauy.top",
                "https://7fdc-2800-a4-11bc-8800-d561-166e-d771-2a27.ngrok-free.app"
        ));

        // métodos y cabeceras permitidas
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // necesario para que el navegador acepte cookies / auth headers
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // aplica esta política a todas las rutas
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
