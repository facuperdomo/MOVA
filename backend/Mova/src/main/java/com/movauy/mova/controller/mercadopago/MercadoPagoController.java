package com.movauy.mova.controller.mercadopago;

import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPConfException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Preference;
import com.mercadopago.resources.Preference.AutoReturn;
import com.mercadopago.resources.datastructures.preference.BackUrls;
import com.mercadopago.resources.datastructures.preference.Item;
import com.mercadopago.resources.datastructures.preference.Payer;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/mercadopago", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://movauy.top",
    "https://movauy.top:8443"
})
@Slf4j
public class MercadoPagoController {

    private final AuthService authService;
    private final BranchRepository branchRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public MercadoPagoController(AuthService authService, BranchRepository branchRepository) {
        this.authService = authService;
        this.branchRepository = branchRepository;
    }

    @PostMapping(path = "/create-preference/{branchId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createPreference(
            @PathVariable Long branchId,
            @RequestBody PaymentRequest request
    ) {
        log.info("🔔 createPreference invoked for branchId={} amount={}", branchId, request.getAmount());

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
        String accessToken = branch.getMercadoPagoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("🛑 No hay MP accessToken para branchId={}", branchId);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La sucursal no tiene configurado un Access Token"));
        }

        try {
            // seteamos el token de MP
            MercadoPago.SDK.setAccessToken(accessToken);
            log.debug("🔑 MP SDK configurado con token de sucursal");

            Preference pref = new Preference()
                    .setPayer(new Payer().setEmail("anonymous@movauy.com"))
                    .appendItem(new Item()
                            .setTitle("Servicio en el bar")
                            .setQuantity(1)
                            .setCurrencyId("UYU")
                            .setUnitPrice(request.getAmount()))
                    .setBackUrls(new BackUrls()
                            .setSuccess(baseUrl + "/success")
                            .setPending(baseUrl + "/pending")
                            .setFailure(baseUrl + "/failure"))
                    .setAutoReturn(AutoReturn.approved)
                    .setNotificationUrl(baseUrl + "/api/webhooks/mercadopago");
            pref.save();

            log.debug("🔔 Preference saved: id={} init_point={} sandbox_init_point={}",
                    pref.getId(), pref.getInitPoint(), pref.getSandboxInitPoint());

            // fallback a sandbox
            String initPoint = pref.getInitPoint() != null
                    ? pref.getInitPoint()
                    : pref.getSandboxInitPoint();

            if (initPoint == null) {
                log.error("❌ Ni init_point ni sandbox_init_point vinieron en la respuesta de MP");
                return ResponseEntity.status(500)
                        .body(Map.of("error", "No se obtuvo init_point de MercadoPago"));
            }

            return ResponseEntity.ok(Map.of("init_point", initPoint));

        } catch (MPConfException e) {
            log.error("❌ MPConfException: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error",
                    "Error de configuración de MercadoPago: " + e.getMessage()));
        } catch (MPException e) {
            log.error("❌ MPException: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error",
                    "Error al crear preferencia: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Excepción inesperada: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error",
                    "Error inesperado: " + e.getMessage()));
        }
    }

    public static class PaymentRequest {

        private Float amount;

        public Float getAmount() {
            return amount;
        }

        public void setAmount(Float amount) {
            this.amount = amount;
        }
    }
}
