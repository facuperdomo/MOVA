package com.movauy.mova.controller.mercadopago;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentResultController {

    @GetMapping("/success")
    public String paymentSuccess() {
        return "Pago aprobado, ¡gracias por su compra!";
    }

    @GetMapping("/pending")
    public String paymentPending() {
        return "Pago pendiente. Por favor, espere la confirmación.";
    }

    @GetMapping("/failure")
    public String paymentFailure() {
        return "El pago no se pudo completar. Inténtelo nuevamente.";
    }
}