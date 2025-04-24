package com.movauy.mova.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/ws/**")
                .allowedOrigins(
                        "http://localhost:3000",
                        "https://movauy.top",
                        "https://7fdc-2800-a4-11bc-8800-d561-166e-d771-2a27.ngrok-free.app"
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowCredentials(true);
    }
}
