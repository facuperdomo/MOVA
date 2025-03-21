package com.movauy.mova.controller.admin;

import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.service.finance.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Facundo
 */
@RestController
@RequestMapping("/api/cash-register")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // Allow React frontend requests
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    @GetMapping("/status")
    public ResponseEntity<Boolean> isCashRegisterOpen() {
        return ResponseEntity.ok(cashRegisterService.getOpenCashRegister().isPresent());
    }

    @PostMapping("/open")
    public ResponseEntity<String> openCashRegister(@RequestBody Map<String, Double> request) {
        Double initialAmount = request.get("initialAmount");
        if (initialAmount == null || initialAmount <= 0) {
            return ResponseEntity.badRequest().body("Monto inicial debe ser mayor a 0.");
        }

        if (cashRegisterService.getOpenCashRegister().isPresent()) {
            return ResponseEntity.badRequest().body("Ya hay una caja abierta.");
        }

        return cashRegisterService.openCashRegister(initialAmount)
                ? ResponseEntity.ok("Caja abierta correctamente.")
                : ResponseEntity.badRequest().body("Error al abrir caja.");
    }

    /**
     * ✅ Cierra la caja y devuelve: - Total vendido - Monto esperado
     */
    @PostMapping("/close")
    public ResponseEntity<?> closeCashRegister() {
        Map<String, Object> result = cashRegisterService.closeCashRegister();

        if (result != null && !result.isEmpty()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body("No hay caja abierta para cerrar.");
        }
    }
}
