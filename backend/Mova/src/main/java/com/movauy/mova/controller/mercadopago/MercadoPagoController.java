package com.movauy.mova.controller.mercadopago;

import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPConfException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Preference;
import com.mercadopago.resources.Preference.AutoReturn;
import com.mercadopago.resources.datastructures.preference.BackUrls;
import com.mercadopago.resources.datastructures.preference.Item;
import com.movauy.mova.model.user.User;
import com.movauy.mova.service.user.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://7fdc-2800-a4-11bc-8800-d561-166e-d771-2a27.ngrok-free.app",
    "https://movauy.top",
    "https://movauy.top:8443"
})
@RestController
@RequestMapping(path = "/api/mercadopago", produces = MediaType.APPLICATION_JSON_VALUE)
public class MercadoPagoController {

    private final AuthService authService;

    /** Dominio público de tu backend, p.ej. https://movauy.top:8443 */
    @Value("${app.base-url}")
    private String baseUrl;

    public MercadoPagoController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(path = "/create-preference/{companyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createPreference(
            @PathVariable Long companyId,
            @RequestBody PaymentRequest request
    ) {
        log.info("🔔 createPreference invoked for companyId={} amount={}", companyId, request.getAmount());

        // 1) Validar empresa
        User company = authService.getUserById(companyId);
        if (company == null || !"COMPANY".equals(company.getRole().name())) {
            log.warn("🛑 Empresa inválida o no encontrada: id={}", companyId);
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Empresa no encontrada o no es de tipo COMPANY"));
        }

        // 2) Token de MP
        String accessToken = company.getMercadoPagoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("🛑 AccessToken no configurado para empresa id={}", companyId);
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "La empresa no tiene configurado un Access Token de MercadoPago"));
        }

        try {
            // 3) Configurar SDK y crear preferencia
            MercadoPago.SDK.setAccessToken(accessToken);
            log.debug("🔑 SDK MP configurado para companyId={}", companyId);

            Preference pref = new Preference()
                .appendItem(new Item()
                    .setTitle("Servicio en el bar")
                    .setQuantity(1)
                    .setCurrencyId("UYU")
                    .setUnitPrice(request.getAmount())
                );
            pref.setBackUrls(new BackUrls()
                    .setSuccess(baseUrl + "/success")
                    .setPending(baseUrl + "/pending")
                    .setFailure(baseUrl + "/failure")
            );
            pref.setAutoReturn(AutoReturn.approved);
            pref.setNotificationUrl(baseUrl + "/api/webhooks/mercadopago");

            pref.save();
            String initPoint = pref.getInitPoint();
            log.info("✅ Preferencia creada con init_point={}", initPoint);

            return ResponseEntity.ok(
                Collections.singletonMap("init_point", initPoint)
            );

        } catch (MPConfException e) {
            log.error("❌ Error de configuración de MercadoPago: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error de configuración de MercadoPago: " + e.getMessage()));
        } catch (MPException e) {
            log.error("❌ MPException al crear preferencia: {}", e.getMessage(), e);
            String msg = e.getMessage() != null
                       ? e.getMessage()
                       : "Error desconocido al crear la preferencia";
            return ResponseEntity.status(500)
                    .body(Map.of("error", msg));
        } catch (Exception e) {
            log.error("❌ Excepción inesperada al crear preferencia: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error inesperado: " + e.getMessage()));
        }
    }

    public static class PaymentRequest {
        private Float amount;
        public Float getAmount() { return amount; }
        public void setAmount(Float amount) { this.amount = amount; }
    }
}
