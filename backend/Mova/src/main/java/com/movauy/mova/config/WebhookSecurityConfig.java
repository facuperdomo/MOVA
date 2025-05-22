// src/main/java/com/movauy/mova/config/WebhookSecurityConfig.java
package com.movauy.mova.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebhookSecurityConfig {

    @Bean
    @Order(1)  // Se evalúa antes que el SecurityConfig “normal”
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
          // Empareja tanto el path exacto como cualquier sub‐ruta
          .securityMatcher("/api/webhooks/mercadopago", "/api/webhooks/mercadopago/**")
          .csrf(csrf -> csrf.disable())
          .cors(cors -> cors.disable())
          .authorizeHttpRequests(a -> a
              .anyRequest().permitAll()
          );
        return http.build();
    }
}
