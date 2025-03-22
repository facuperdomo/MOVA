package com.movauy.mova.controller.mercadopago;

import com.mercadopago.MercadoPago;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.Preference;
import com.mercadopago.resources.Preference.AutoReturn;
import com.mercadopago.resources.datastructures.preference.BackUrls;
import com.mercadopago.resources.datastructures.preference.Item;
import com.mercadopago.resources.datastructures.preference.Payer;
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

    // Reemplaza este token por el token correcto (usa sandbox o producción según corresponda)
    private static final String ACCESS_TOKEN = "APP_USR-2196167234808357-032023-4d5d077ea1e0bf4fb759b1ede659d1fd-2296928739";

    @PostMapping("/create-preference")
    public ResponseEntity<?> createPreference(@RequestBody PaymentRequest request) {
        try {
            // Configurar el SDK con el Access Token
            MercadoPago.SDK.setAccessToken(ACCESS_TOKEN);

            // Crear la preferencia de pago
            Preference preference = new Preference();

            // Establecer el payer (obligatorio)
            Payer payer = new Payer();
            // Este email debe coincidir con el usuario de prueba proporcionado por MercadoPago
            payer.setEmail("test_user_1023298951@testuser.com");
            preference.setPayer(payer);

            // Configurar el ítem a cobrar
            Item item = new Item();
            item.setTitle("Servicio en el bar")
                    .setQuantity(1)
                    .setCurrencyId("UYU")
                    .setUnitPrice(request.getAmount());
            preference.appendItem(item);

            // Configurar las URLs de retorno
            BackUrls backUrls = new BackUrls()
                    .setSuccess("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/success")
                    .setPending("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/pending")
                    .setFailure("https://e96b-2800-a4-11d4-7f00-41d7-8c68-c74d-772b.ngrok-free.app/failure");
            preference.setBackUrls(backUrls);

            // Configurar auto-retorno al aprobar el pago
            preference.setAutoReturn(AutoReturn.approved);

            // Guardar la preferencia y contactar con los servidores de MercadoPago
            preference.save();

            // Devolver el init_point para redirigir al pago
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
