package com.movauy.mova.config;

import com.movauy.mova.Jwt.JwtAuthenticationFilter;
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

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authProvider;
    private final BridgeTokenFilter bridgeTokenFilter;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web
                .ignoring()
                .requestMatchers("/api/webhooks/mercadopago");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e)
                        -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage()))
                .accessDeniedHandler((req, res, e)
                        -> res.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage()))
                )
                .authorizeHttpRequests(auth -> auth
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Auth públicos
                .requestMatchers(HttpMethod.POST,
                        "/auth/loginUser", "/auth/loginCompany", "/auth/loginBranch",
                        "/auth/refresh-token", "/auth/register"
                ).permitAll()
                // Otros públicos
                .requestMatchers(
                        "/error/**", "/actuator/health", "/actuator/info", "/api/branches"
                ).permitAll()
                // WS y creación de preference MP
                .requestMatchers("/ws/**", "/ws-sockjs/**", "/api/mercadopago/**")
                .permitAll()
                // /auth/** sí necesita JWT
                .requestMatchers("/auth/**").authenticated()
                // Resto (API privadas) necesitan JWT o Bridge‐Token
                .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
