package com.movauy.mova.controller.webhooks;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private final SimpMessagingTemplate messagingTemplate;

    public MercadoPagoWebhookController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<?> receiveNotification(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        System.out.println("Webhook recibido de MercadoPago:");
        System.out.println(payload);

        // Determinar el estado del pago basándose en el payload.
        // Se asume que en un evento de pago se incluye una propiedad "action" y, si es posible, "status".
        String paymentStatus = "Desconocido";
        if (payload.containsKey("action")) {
            String action = payload.get("action").toString();
            if (action.contains("payment")) {
                // Si el payload incluye "status", lo usamos; de lo contrario, usamos la acción como indicador.
                if (payload.containsKey("status")) {
                    paymentStatus = payload.get("status").toString();
                } else {
                    paymentStatus = action;
                }
            } else {
                paymentStatus = "Evento recibido de tipo: " + action;
            }
        }
        
        // Envía el estado del pago a través de WebSocket a todos los clientes suscritos al canal "/topic/payment-status"
        System.out.println("Enviando mensaje de estado de pago: " + paymentStatus);
        messagingTemplate.convertAndSend("/topic/payment-status", paymentStatus);
        
        return ResponseEntity.ok().build();
    }
}
