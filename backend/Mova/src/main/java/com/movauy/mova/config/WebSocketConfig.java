package com.movauy.mova.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1) endpoint “puro” WS/SOCKETS para brokerURL="wss://.../ws"
        registry
                .addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "https://7fdc-2800-a4-11bc-8800-d561-166e-d771-2a27.ngrok-free.app"
                );

        // 2) exactamente el mismo + SockJS fallback (opcional)
        registry
                .addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "https://movauy.top",
                        "https://movauy.top:8443",
                        "https://7fdc-2800-a4-11bc-8800-d561-166e-d771-2a27.ngrok-free.app"
                )
                .withSockJS();
    }
}
