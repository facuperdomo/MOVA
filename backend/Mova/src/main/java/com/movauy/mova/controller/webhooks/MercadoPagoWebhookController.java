package com.movauy.mova.controller.webhooks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mercadopago")
@Slf4j
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
        log.info("🔔 Webhook recibido de MercadoPago: {}", payload);

        String paymentStatus = "unknown";

        // 1) Payload de prueba que viene con "status"
        if (payload.containsKey("status")) {
            paymentStatus = payload.get("status").toString().toLowerCase();
        }
        // 2) Formato real de MP: type + data.id
        else if (payload.containsKey("type") && payload.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String type = payload.get("type").toString();
            String mpId = data.get("id").toString();

            try {
                if ("payment".equals(type)) {
                    paymentStatus = com.mercadopago.resources.Payment
                                        .findById(mpId)
                                        .getStatus()
                                        .toString()
                                        .toLowerCase();
                } else if ("merchant_order".equals(type)) {
                    var mo = com.mercadopago.resources.MerchantOrder.findById(mpId);
                    if (!mo.getPayments().isEmpty()) {
                        paymentStatus = mo.getPayments()
                                          .get(0)
                                          .getStatus()
                                          .toString()
                                          .toLowerCase();
                    }
                }
            } catch (Exception ex) {
                log.error("❌ Error al consultar MP ID=" + mpId, ex);
            }
        }

        // 3) Emitimos sólo estados finales
        if ("approved".equalsIgnoreCase(paymentStatus) ||
            "rejected".equalsIgnoreCase(paymentStatus)) {
            log.info("▶️ Enviando estado final: {}", paymentStatus);
            messagingTemplate.convertAndSend(
                "/topic/payment-status",
                paymentStatus
            );
        }

        return ResponseEntity.ok().build();
    }
}
