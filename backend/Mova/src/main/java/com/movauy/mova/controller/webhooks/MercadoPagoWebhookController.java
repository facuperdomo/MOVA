package com.movauy.mova.controller.webhooks;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    @PostMapping
    public ResponseEntity<?> receiveNotification(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        System.out.println("Webhook recibido de MercadoPago:");
        System.out.println(payload);

        if (payload.containsKey("type")) {
            String type = payload.get("type").toString();
            if ("payment".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data != null && data.containsKey("id")) {
                    Long paymentId = Long.parseLong(data.get("id").toString());
                    System.out.println("Notificación de pago: ID = " + paymentId);
                }
            } else {
                System.out.println("Evento recibido de tipo: " + type);
            }
        }

        return ResponseEntity.ok().build();
    }
}
