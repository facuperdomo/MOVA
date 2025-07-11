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
        Long branchId = null;
        Long userId = null;

        if (payload.containsKey("type") && payload.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            String type = payload.get("type").toString();
            String mpId = data.get("id").toString();

            try {
                if ("payment".equals(type)) {
                    var mpPayment = com.mercadopago.resources.Payment.findById(mpId);
                    paymentStatus = mpPayment.getStatus().toString().toLowerCase();

                    String externalRef = mpPayment.getExternalReference(); // 🔥
                    log.info("ℹ️ external_reference='{}'", externalRef);
                    if (externalRef != null && externalRef.contains("_user-")) {
                        String[] parts = externalRef.split("_");
                        branchId = Long.parseLong(parts[0].replace("branch-", ""));
                        userId = Long.parseLong(parts[1].replace("user-", ""));
                    }

                } else if ("merchant_order".equals(type)) {
                    var mo = com.mercadopago.resources.MerchantOrder.findById(mpId);
                    if (!mo.getPayments().isEmpty()) {
                        paymentStatus = mo.getPayments().get(0).getStatus().toString().toLowerCase();
                    }
                }
            } catch (Exception ex) {
                log.error("❌ Error al consultar MP ID=" + mpId, ex);
            }
        }

        // Emitimos solo si hay branchId y estado final
        if (branchId != null && userId != null
                && ("approved".equalsIgnoreCase(paymentStatus)
                || "rejected".equalsIgnoreCase(paymentStatus))) {

            log.info("▶️ Enviando estado '{}' a user {} en branch {}", paymentStatus, userId, branchId);

            // Ahora enviamos al canal del usuario
            messagingTemplate.convertAndSend(
                    "/topic/payment-status/user/" + userId,
                    paymentStatus
            );
        }

        return ResponseEntity.ok().build();
    }
}
