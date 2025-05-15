package com.movauy.mova.controller.admin;

import com.movauy.mova.service.finance.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador para la administración de caja.
 */
@RestController
@RequestMapping("/api/cash-register")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://movauy.top:8443")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    // Se requiere el token para saber a qué empresa corresponde el estado de caja
    @GetMapping("/status")
    public ResponseEntity<Boolean> isCashRegisterOpen(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(cashRegisterService.getOpenCashRegister(token).isPresent());
    }

    // Se utiliza el token para abrir la caja de la empresa autenticada
    @PostMapping("/open")
    public ResponseEntity<String> openCashRegister(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Double> request) {

        Double initialAmount = request.get("initialAmount");
        if (initialAmount == null || initialAmount <= 0) {
            return ResponseEntity.badRequest().body("Monto inicial debe ser mayor a 0.");
        }

        // Verifica si ya hay una caja abierta para esta empresa
        if (cashRegisterService.getOpenCashRegister(token).isPresent()) {
            return ResponseEntity.badRequest().body("Ya hay una caja abierta para esta sucursal.");
        }

        return cashRegisterService.openCashRegister(token, initialAmount)
                ? ResponseEntity.ok("Caja abierta correctamente.")
                : ResponseEntity.badRequest().body("Error al abrir caja.");
    }

    // Se incluye el token para cerrar la caja de la empresa autenticada
    @PostMapping("/close")
    public ResponseEntity<?> closeCashRegister(@RequestHeader("Authorization") String token) {
        Map<String, Object> result = cashRegisterService.closeCashRegister(token);
        if (result != null && !result.isEmpty()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body("No hay caja abierta para cerrar.");
        }
    }
}
