package com.movauy.mova.controller.mercadopago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPConfException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Preference;
import com.mercadopago.resources.Preference.AutoReturn;
import com.mercadopago.resources.datastructures.preference.BackUrls;
import com.mercadopago.resources.datastructures.preference.Item;
import com.mercadopago.resources.datastructures.preference.Payer;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final ObjectMapper mapper = new ObjectMapper();

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

        // 1) Validar sucursal y token MP
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
        String accessToken = branch.getMercadoPagoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La sucursal no tiene configurado un Access Token de MercadoPago"));
        }

        try {
            // 2) Configurar SDK y crear preferencia
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

            // 3) Leer JSON bruto de la respuesta MP
            String rawJson = pref.getLastApiResponse()
                    .getJsonElementResponse()
                    .toString();
            log.debug("🎯 MP RAW JSON response:\n{}", rawJson);

            // 4) Parsear con Jackson para extraer los campos
            JsonNode root = mapper.readTree(rawJson);
            // a veces viene dentro de un objeto "response"
            JsonNode data = root.has("response") ? root.get("response") : root;

            String initPoint = data.path("init_point").asText(null);
            String sandboxInitPoint = data.path("sandbox_init_point").asText(null);
            log.debug("▶︎ init_point='{}', sandbox_init_point='{}'", initPoint, sandboxInitPoint);

            // 5) Fallback a sandbox si hace falta init_point
            if (initPoint == null && sandboxInitPoint != null) {
                log.warn("⚠️ init_point nulo, usando sandbox_init_point");
                initPoint = sandboxInitPoint;
            }
            if (initPoint == null) {
                log.error("❌ Ni init_point ni sandbox_init_point recibidos de MP, rawJson={}", rawJson);
                // Devuelvo también el rawJson para depurar en Postman/Front
                Map<String, Object> err = new HashMap<>();
                err.put("error", "No se obtuvo init_point ni sandbox_init_point de MercadoPago");
                err.put("rawJson", rawJson);
                return ResponseEntity.status(500).body(err);
            }

            // 6) Responder al frontend
            return ResponseEntity.ok(Map.of("init_point", initPoint));

        } catch (MPConfException e) {
            log.error("❌ Error de configuración de MercadoPago: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error de configuración de MercadoPago: " + e.getMessage()));
        } catch (MPException e) {
            log.error("❌ MPException al crear preferencia: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al crear preferencia: " + e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Excepción inesperada al crear preferencia: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
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
