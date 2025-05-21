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
            @RequestHeader("Authorization") String token,
            @RequestBody PaymentRequest request
    ) {
        log.info("🔔 createPreference invoked for branchId={} amount={}", branchId, request.getAmount());

        // 1) Validar sucursal
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

        String accessToken = branch.getMercadoPagoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "La sucursal no tiene configurado un Access Token de MercadoPago"));
        }

        try {
            MercadoPago.SDK.setAccessToken(accessToken);

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
            return ResponseEntity.ok(Map.of("init_point", pref.getInitPoint()));

        } catch (MPConfException e) {
            log.error("❌ Error de configuración de MercadoPago: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Error de configuración de MercadoPago: " + e.getMessage()));
        } catch (MPException e) {
            log.error("❌ MPException al crear preferencia: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Error al crear preferencia: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Excepción inesperada al crear preferencia: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Error inesperado: " + e.getMessage()));
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
