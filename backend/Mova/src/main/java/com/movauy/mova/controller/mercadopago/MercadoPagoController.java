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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mercadopago")
public class MercadoPagoController {

    private final AuthService authService;

    /** URL pública de tu servidor (ngrok o dominio), definida en application.properties */
    @Value("${app.base-url}")
    private String baseUrl;

    public MercadoPagoController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/create-preference/{companyId}")
    public ResponseEntity<?> createPreference(
            @PathVariable Long companyId,
            @RequestBody PaymentRequest request
    ) {
        // 1) Validar empresa
        User company = authService.getUserById(companyId);
        if (company == null || !"COMPANY".equals(company.getRole().name())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Empresa no encontrada o no es de tipo COMPANY"));
        }

        // 2) Token de MP
        String accessToken = company.getMercadoPagoAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "La empresa no tiene configurado un Access Token de MercadoPago"));
        }

        try {
            // Configurar SDK
            MercadoPago.SDK.setAccessToken(accessToken);

            // 3) Crear preferencia
            Preference pref = new Preference();

            // Ítem
            pref.appendItem(new Item()
                    .setTitle("Servicio en el bar")
                    .setQuantity(1)
                    .setCurrencyId("UYU")
                    .setUnitPrice(request.getAmount())
            );

            // URLs de retorno
            pref.setBackUrls(new BackUrls()
                    .setSuccess(baseUrl + "/success")
                    .setPending(baseUrl + "/pending")
                    .setFailure(baseUrl + "/failure")
            );
            pref.setAutoReturn(AutoReturn.approved);

            // Webhook de notificaciones
            pref.setNotificationUrl(baseUrl + "/api/webhooks/mercadopago");

            // Guardar y obtener link
            pref.save();

            return ResponseEntity.ok(Map.of("init_point", pref.getInitPoint()));

        } catch (MPConfException e) {
            // Error de configuración (por ejemplo credenciales mal cargadas)
            e.printStackTrace();
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Error de configuración de MercadoPago: " + e.getMessage()));

        } catch (MPException e) {
            // Cualquier otro error de la SDK de MercadoPago
            e.printStackTrace();
            String msg = e.getMessage() == null
                         ? "Error desconocido al crear la preferencia"
                         : e.getMessage();
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", msg));
        }
    }

    public static class PaymentRequest {
        private Float amount;
        public Float getAmount() { return amount; }
        public void setAmount(Float amount) { this.amount = amount; }
    }
}
