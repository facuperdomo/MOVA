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
            @RequestBody(required = false) Map<String, Object> payload,
            @RequestParam(value = "topic", required = false) String topicParam,
            @RequestParam(value = "id", required = false) String idParam) {

        log.info("🔔 Webhook recibido de MercadoPago: payload={}, topic={}, id={}", payload, topicParam, idParam);

        // Determinar tipo y mpId ya sea de query params o de JSON
        String type = topicParam;
        String mpId = idParam;
        if (type == null && payload != null) {
            type = (String) payload.get("type");
        }
        if (mpId == null && payload != null && payload.containsKey("data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            Object idObj = data.get("id");
            mpId = idObj != null ? idObj.toString() : null;
        }

        if (type == null || mpId == null) {
            log.warn("Webhook sin tipo o id válidos (type={}, mpId={}), ignorando.", type, mpId);
            return ResponseEntity.ok().build();
        }

        String paymentStatus = "unknown";
        Long branchId = null, userId = null;

        try {
            if ("payment".equalsIgnoreCase(type)) {
                var mpPayment = com.mercadopago.resources.Payment.findById(mpId);
                if (mpPayment.getStatus() != null) {
                    paymentStatus = mpPayment.getStatus().toString().toLowerCase();
                }

                String externalRef = mpPayment.getExternalReference();
                log.info("ℹ️ external_reference='{}'", externalRef);
                if (externalRef != null && externalRef.contains("_user-")) {
                    String[] parts = externalRef.split("_");
                    branchId = Long.parseLong(parts[0].replace("branch-", ""));
                    userId = Long.parseLong(parts[1].replace("user-", ""));
                }

            } else if ("merchant_order".equalsIgnoreCase(type)) {
                var mo = com.mercadopago.resources.MerchantOrder.findById(mpId);
                if (!mo.getPayments().isEmpty() && mo.getPayments().get(0).getStatus() != null) {
                    paymentStatus = mo.getPayments().get(0).getStatus().toString().toLowerCase();
                }
            }
        } catch (Exception ex) {
            log.error("❌ Error al consultar MP ID=" + mpId, ex);
        }

        // Emitimos solo si extraímos branchId, userId y es estado final
        if (branchId != null && userId != null
                && ("approved".equalsIgnoreCase(paymentStatus) || "rejected".equalsIgnoreCase(paymentStatus))) {

            log.info("▶️ Enviando estado '{}' a user {} en branch {}", paymentStatus, userId, branchId);
            messagingTemplate.convertAndSend(
                    "/topic/payment-status/user/" + userId,
                    paymentStatus
            );
        }

        return ResponseEntity.ok().build();
    }
}
