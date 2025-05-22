package com.movauy.mova.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

@Configuration
public class WebSecurityIgnoreConfig {

    /**
     * Ignora por completo cualquier filtro de Spring Security
     * (incluyendo JWT, BridgeToken, etc.) en /api/mercadopago/**
     */
    @Bean
    public WebSecurityCustomizer webSecurityIgnoreCustomizer() {
        return (web) -> web
            .ignoring()
            .requestMatchers("/api/mercadopago/**");
    }
}
