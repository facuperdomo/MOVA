package com.movauy.mova.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebhookSecurityConfig {

    /**
     * Este filter‐chain se evalúa primero (Order(1)). 
     * Sólo afecta a /api/webhooks/mercadopago/** y lo deja 100% libre de seguridad.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
          // 1) Sólo matchea TODO lo que empiece con /api/webhooks/mercadopago/
          .securityMatcher("/api/webhooks/mercadopago/**")

          // 2) Deshabilitamos CSRF y CORS aquí (no queremos que nos metas cabeceras extrañas)
          .csrf(csrf -> csrf.disable())
          .cors(cors -> cors.disable())

          // 3) Y finalmente: PERMIT ALL
          .authorizeHttpRequests(a -> a
              .anyRequest().permitAll()
          );

        return http.build();
    }
}
