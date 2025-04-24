package com.movauy.mova.controller.webhooks;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

import com.mercadopago.resources.Payment;
import com.mercadopago.resources.MerchantOrder;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private final SimpMessagingTemplate messagingTemplate;

    public MercadoPagoWebhookController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public ResponseEntity<Void> receiveNotification(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request
    ) {
        System.out.println("Webhook recibido de MercadoPago:");
        System.out.println(payload);

        String paymentStatus = "Desconocido";

        // 1) Si es tu payload de prueba (Postman) con "status"
        if (payload.containsKey("status")) {
            paymentStatus = payload.get("status").toString();
        } // 2) Si es el formato real de MP (type + data.id)
        else if (payload.containsKey("type") && payload.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String type = payload.get("type").toString();
            String mpId = data.get("id").toString();

            try {
                if ("payment".equals(type)) {
                    com.mercadopago.resources.Payment p
                            = com.mercadopago.resources.Payment.findById(mpId);
                    paymentStatus = p.getStatus().toString();
                } else if ("merchant_order".equals(type)) {
                    com.mercadopago.resources.MerchantOrder mo
                            = com.mercadopago.resources.MerchantOrder
                                    .findById(mpId);
                    paymentStatus = mo.getPayments().get(0).getStatus().toString();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // 3) Sólo enviamos los estados finales
        if ("approved".equalsIgnoreCase(paymentStatus)
                || "rejected".equalsIgnoreCase(paymentStatus)) {
            System.out.println("Enviando estado final: " + paymentStatus);
            messagingTemplate.convertAndSend(
                    "/topic/payment-status",
                    paymentStatus.toLowerCase()
            );
        }

        return ResponseEntity.ok().build();
    }
}
