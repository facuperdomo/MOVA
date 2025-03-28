package com.movauy.mova.controller.mercadopago;

import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Preference;
import com.mercadopago.resources.Preference.AutoReturn;
import com.mercadopago.resources.datastructures.preference.BackUrls;
import com.mercadopago.resources.datastructures.preference.Item;
import com.mercadopago.resources.datastructures.preference.Payer;
import com.movauy.mova.model.user.User;
import com.movauy.mova.service.user.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para gestionar preferencias de pago con MercadoPago.
 */
@RestController
@RequestMapping("/api/mercadopago")
public class MercadoPagoController {

    private final AuthService authService;

    public MercadoPagoController(AuthService authService) {
        this.authService = authService;
    }

    // Endpoint que recibe el ID de la empresa (User de tipo COMPANY)
    @PostMapping("/create-preference/{companyId}")
    public ResponseEntity<?> createPreference(@PathVariable("companyId") Long companyId, @RequestBody PaymentRequest request) {
        try {
            // Obtener la empresa usando el método del AuthService
            User company = authService.getUserById(companyId);
            if (company == null || !company.getRole().name().equals("COMPANY")) {
                return ResponseEntity.badRequest().body("Empresa no encontrada o no es de tipo COMPANY");
            }

            // Extraer el Access Token de MercadoPago almacenado en la entidad User
            // Extraer el Access Token de MercadoPago almacenado en la entidad User
            String accessToken = company.getMercadoPagoAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                // Devuelve un error JSON indicando específicamente que falta el Access Token
                Map<String, String> error = new HashMap<>();
                error.put("error", "La empresa no tiene configurado un Access Token de MercadoPago");
                return ResponseEntity.badRequest().body(error);
            }

            // Configurar el SDK con el Access Token de la empresa
            MercadoPago.SDK.setAccessToken(accessToken);

            // Crear la preferencia de pago
            Preference preference = new Preference();

            // Establecer el payer (puedes ajustar el email de prueba o parametrizarlo)
            Payer payer = new Payer();
            payer.setEmail("test_user_1023298951@testuser.com");
            preference.setPayer(payer);

            // Configurar el ítem a cobrar
            Item item = new Item();
            item.setTitle("Servicio en el bar")
                    .setQuantity(1)
                    .setCurrencyId("UYU")
                    .setUnitPrice(request.getAmount());
            preference.appendItem(item);

            // Configurar las URLs de retorno (asegúrate de usar una URL pública, por ejemplo, con ngrok)
            BackUrls backUrls = new BackUrls()
                    .setSuccess("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/success")
                    .setPending("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/pending")
                    .setFailure("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/failure");
            preference.setBackUrls(backUrls);

            // Configurar auto-retorno al aprobar el pago
            preference.setAutoReturn(AutoReturn.approved);

            // Guardar la preferencia y contactar con los servidores de MercadoPago
            preference.save();

            // Devolver el init_point para redirigir al pago (y para generar el QR)
            Map<String, String> response = new HashMap<>();
            response.put("init_point", preference.getInitPoint());
            return ResponseEntity.ok(response);

        } catch (MPException e) {
            e.printStackTrace();
            String errorMsg = (e.getMessage() == null || e.getMessage().isEmpty())
                    ? "Error desconocido al crear la preferencia"
                    : e.getMessage();
            Map<String, String> error = new HashMap<>();
            error.put("error", errorMsg);
            return ResponseEntity.status(500).body(error);
        }
    }
}

class PaymentRequest {

    private Float amount;

    public Float getAmount() {
        return amount;
    }

    public void setAmount(Float amount) {
        this.amount = amount;
    }
}
