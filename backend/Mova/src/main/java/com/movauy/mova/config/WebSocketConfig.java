package com.movauy.mova.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // tus topics y prefijos de aplicación siguen igual
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1) Endpoint puro para WebSocket (handshake 101) — lo usará la tablet Android
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                    "http://localhost:3000",
                    "https://movauy.top",
                    "https://www.movauy.top"
                );

        // 2) (Opcional) Si quieres seguir ofreciendo SockJS para el frontend web:
        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(
                    "http://localhost:3000",
                    "https://movauy.top",
                    "https://www.movauy.top"
                )
                .withSockJS();
    }
}
