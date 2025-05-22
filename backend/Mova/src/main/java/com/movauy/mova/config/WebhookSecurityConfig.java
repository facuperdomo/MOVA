package com.movauy.mova.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebhookSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain webhookFilterChain(HttpSecurity http) throws Exception {
        http
          // Solo matchea este path y lo deja libre de filtros
          .securityMatcher("/api/webhooks/mercadopago")
          .csrf(csrf -> csrf.disable())
          .cors(cors -> cors.disable())
          .authorizeHttpRequests(auth -> auth
              .anyRequest().permitAll()
          );
        return http.build();
    }
}
